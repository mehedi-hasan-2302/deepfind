use std::{
    io::{BufRead, BufReader, Read, Write},
    net::{Ipv4Addr, SocketAddrV4, TcpStream},
    process::{Child, Command, Stdio},
    sync::{
        atomic::{AtomicBool, Ordering},
        mpsc, Mutex,
    },
    thread,
    time::{Duration, Instant},
};
use tauri::{path::BaseDirectory, AppHandle, Manager, Url};

#[derive(Default)]
pub struct Backend {
    child: Mutex<Option<Child>>,
    #[cfg(windows)]
    job: Mutex<Option<std::os::windows::io::OwnedHandle>>,
    pub stopping: AtomicBool,
}

impl Backend {
    pub fn start(&self, app: &AppHandle) -> Result<u16, &'static str> {
        let java = app
            .path()
            .resolve("runtime/bin/java.exe", BaseDirectory::Resource)
            .map_err(|_| "RUNTIME_MISSING")?;
        let jar = app
            .path()
            .resolve("deepfind-backend.jar", BaseDirectory::Resource)
            .map_err(|_| "BACKEND_MISSING")?;
        if !java.is_file() || !jar.is_file() {
            return Err("RESOURCES_MISSING");
        }
        // Tauri canonicalizes to a Windows verbatim (\\?\) path. The JVM JAR launcher
        // cannot load Boot's entry class from that spelling; simplify representable paths.
        let mut command = Command::new(dunce::simplified(&java));
        command
            .args(["-Xmx512m", "-jar"])
            .arg(dunce::simplified(&jar))
            .args([
                "--deepfind.desktop=true",
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--spring.main.banner-mode=off",
            ])
            .stdin(Stdio::piped())
            .stdout(Stdio::piped())
            .stderr(Stdio::null());
        // User-controlled JVM injection variables must not change a packaged runtime launch.
        for name in [
            "JAVA_TOOL_OPTIONS",
            "_JAVA_OPTIONS",
            "JDK_JAVA_OPTIONS",
            "CLASSPATH",
        ] {
            command.env_remove(name);
        }
        #[cfg(windows)]
        {
            use std::os::windows::process::CommandExt;
            command.creation_flags(0x08000000); // CREATE_NO_WINDOW
        }
        let (sender, receiver) = mpsc::sync_channel(1);
        {
            let mut owned = self.child.lock().map_err(|_| "SUPERVISOR_FAILED")?;
            if self.stopping.load(Ordering::SeqCst) {
                return Err("CLOSING");
            }
            let mut child = command.spawn().map_err(|_| "JAVA_START_FAILED")?;
            #[cfg(windows)]
            match crate::job::contain(&child) {
                Ok(job) => *self.job.lock().expect("job ownership") = Some(job),
                Err(_) => {
                    let _ = child.kill();
                    let _ = child.wait();
                    return Err("PROCESS_CONTAINMENT_FAILED");
                }
            }
            let stdout = child.stdout.take().expect("piped stdout");
            *owned = Some(child);
            // Drain all diagnostics without displaying private paths or retaining an unbounded log.
            thread::spawn(move || {
                let mut reader = BufReader::new(stdout);
                let mut line = Vec::with_capacity(1024);
                loop {
                    let Ok(buffer) = reader.fill_buf() else { break };
                    if buffer.is_empty() {
                        break;
                    }
                    let consumed = buffer
                        .iter()
                        .position(|byte| *byte == b'\n')
                        .map(|n| n + 1)
                        .unwrap_or(buffer.len());
                    let complete = buffer[consumed - 1] == b'\n';
                    let available = 1024usize.saturating_sub(line.len());
                    line.extend_from_slice(&buffer[..consumed.min(available)]);
                    reader.consume(consumed);
                    if complete {
                        if let Some(port) = parse_ready(&line) {
                            let _ = sender.try_send(port);
                        }
                        line.clear();
                    }
                }
            });
        }
        let deadline = Instant::now() + Duration::from_secs(120);
        while Instant::now() < deadline {
            if self.stopping.load(Ordering::SeqCst) {
                return Err("CLOSING");
            }
            if !self.running() {
                return Err("BACKEND_EXITED");
            }
            match receiver.recv_timeout(Duration::from_millis(200)) {
                Ok(port) => {
                    return if healthy(port) {
                        Ok(port)
                    } else {
                        Err("HEALTH_CHECK_FAILED")
                    }
                }
                Err(mpsc::RecvTimeoutError::Disconnected) => return Err("BACKEND_PIPE_CLOSED"),
                Err(mpsc::RecvTimeoutError::Timeout) => {}
            }
        }
        Err("STARTUP_TIMEOUT")
    }

    pub fn running(&self) -> bool {
        self.child
            .lock()
            .ok()
            .and_then(|mut owned| {
                owned
                    .as_mut()
                    .map(|child| matches!(child.try_wait(), Ok(None)))
            })
            .unwrap_or(false)
    }

    pub fn stop(&self) {
        self.stopping.store(true, Ordering::SeqCst);
        // Closing our pipe is a graceful Spring shutdown request, including during startup.
        if let Ok(mut owned) = self.child.lock() {
            if let Some(child) = owned.as_mut() {
                drop(child.stdin.take());
            }
        }
        let deadline = Instant::now() + Duration::from_secs(45);
        while self.running() && Instant::now() < deadline {
            thread::sleep(Duration::from_millis(100));
        }
        if let Ok(mut owned) = self.child.lock() {
            if let Some(mut child) = owned.take() {
                if !matches!(child.try_wait(), Ok(Some(_))) {
                    let _ = child.kill();
                }
                let _ = child.wait();
            }
        }
    }
}

fn parse_ready(line: &[u8]) -> Option<u16> {
    let value = std::str::from_utf8(line)
        .ok()?
        .trim_end_matches(['\r', '\n'])
        .strip_prefix("DEEPFIND_READY ")?;
    if value.is_empty() || !value.bytes().all(|b| b.is_ascii_digit()) {
        return None;
    }
    value.parse::<u16>().ok().filter(|port| *port != 0)
}

fn healthy(port: u16) -> bool {
    let Ok(mut stream) = TcpStream::connect_timeout(
        &SocketAddrV4::new(Ipv4Addr::LOCALHOST, port).into(),
        Duration::from_secs(3),
    ) else {
        return false;
    };
    let _ = stream.set_read_timeout(Some(Duration::from_secs(3)));
    let _ = stream.set_write_timeout(Some(Duration::from_secs(3)));
    if write!(
        stream,
        "GET /api/health HTTP/1.1\r\nHost: 127.0.0.1:{port}\r\nConnection: close\r\n\r\n"
    )
    .is_err()
    {
        return false;
    }
    let mut response = String::new();
    stream.take(8192).read_to_string(&mut response).is_ok()
        && response.starts_with("HTTP/1.1 200 ")
        && response.contains("\"status\":\"UP\"")
}

pub fn allowed_navigation(url: &Url, port: Option<u16>) -> bool {
    if !url.username().is_empty() || url.password().is_some() {
        return false;
    }
    let local_page = matches!(
        url.path(),
        "/" | "/index.html" | "/recovery.html" | "/closing.html"
    );
    if local_page
        && ((url.scheme() == "tauri" && url.host_str() == Some("localhost"))
            || (url.scheme() == "http"
                && url.host_str() == Some("tauri.localhost")
                && url.port().is_none()))
    {
        return true;
    }
    port.is_some()
        && url.scheme() == "http"
        && url.host_str() == Some("127.0.0.1")
        && url.port_or_known_default() == port
}

#[cfg(test)]
mod tests {
    use super::*;

    #[cfg(windows)]
    #[test]
    fn bundled_jar_path_uses_java_compatible_windows_spelling() {
        let verbatim = std::path::Path::new(r"\\?\C:\Program Files\DeepFind\deepfind-backend.jar");
        assert_eq!(
            dunce::simplified(verbatim),
            std::path::Path::new(r"C:\Program Files\DeepFind\deepfind-backend.jar")
        );
    }

    #[test]
    fn handshake_accepts_only_a_valid_port_event() {
        assert_eq!(parse_ready(b"DEEPFIND_READY 12345\r\n"), Some(12345));
        for bad in [
            "DEEPFIND_READY 0",
            "DEEPFIND_READY 65536",
            "log DEEPFIND_READY 3",
            "DEEPFIND_READY +3",
            "DEEPFIND_READY 3 extra",
        ] {
            assert_eq!(parse_ready(bad.as_bytes()), None);
        }
    }

    #[test]
    fn navigation_is_exact_origin_and_never_an_external_protocol() {
        for good in [
            "http://127.0.0.1:12345/",
            "http://127.0.0.1:12345/?q=test",
            "http://tauri.localhost/recovery.html",
        ] {
            assert!(allowed_navigation(&Url::parse(good).unwrap(), Some(12345)));
        }
        for bad in [
            "https://example.com",
            "http://127.0.0.1:12346/",
            "http://localhost:12345/",
            "http://127.0.0.1.evil.test:12345",
            "file:///C:/secret",
            "http://user@127.0.0.1:12345/",
            "javascript:alert(1)",
        ] {
            assert!(!allowed_navigation(&Url::parse(bad).unwrap(), Some(12345)));
        }
        assert!(!allowed_navigation(
            &Url::parse("http://127.0.0.1:12345").unwrap(),
            None
        ));
    }
}
