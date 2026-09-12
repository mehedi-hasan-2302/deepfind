use std::{
    io::{self, Write},
    net::{Ipv4Addr, SocketAddr, TcpListener},
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc,
    },
    thread::{self, JoinHandle},
    time::Duration,
};

/// App-scoped HTTP(S) egress sink. It never reads, logs, resolves, or forwards a request.
/// WebView2's implicit loopback bypass still permits the local UI/backend.
pub struct OfflineProxy {
    address: SocketAddr,
    stopping: Arc<AtomicBool>,
    worker: Option<JoinHandle<()>>,
}

impl OfflineProxy {
    pub fn start() -> io::Result<Self> {
        let listener = TcpListener::bind((Ipv4Addr::LOCALHOST, 0))?;
        let address = listener.local_addr()?;
        listener.set_nonblocking(true)?;
        let stopping = Arc::new(AtomicBool::new(false));
        let stop = stopping.clone();
        let worker = thread::spawn(move || {
            while !stop.load(Ordering::SeqCst) {
                match listener.accept() {
                    Ok((mut stream, _)) => {
                        let _ = stream.set_write_timeout(Some(Duration::from_millis(100)));
                        let _ = stream.write_all(b"HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
                    }
                    Err(error) if error.kind() == io::ErrorKind::WouldBlock => {
                        thread::sleep(Duration::from_millis(20))
                    }
                    Err(_) => break,
                }
            }
        });
        Ok(Self {
            address,
            stopping,
            worker: Some(worker),
        })
    }

    pub fn url(&self) -> tauri::Url {
        format!("http://{}", self.address)
            .parse()
            .expect("loopback proxy URL")
    }
}

impl Drop for OfflineProxy {
    fn drop(&mut self) {
        self.stopping.store(true, Ordering::SeqCst);
        if let Some(worker) = self.worker.take() {
            let _ = worker.join();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{io::Read, net::TcpStream};

    #[test]
    fn rejects_without_forwarding_and_releases_listener() {
        let proxy = OfflineProxy::start().unwrap();
        let address = proxy.address;
        assert!(address.ip().is_loopback());
        let mut stream = TcpStream::connect(address).unwrap();
        stream
            .set_read_timeout(Some(Duration::from_secs(2)))
            .unwrap();
        let mut response = [0u8; 128];
        let count = stream.read(&mut response).unwrap();
        assert!(response[..count].starts_with(b"HTTP/1.1 403 Forbidden"));
        drop(stream);
        drop(proxy);
        assert!(TcpStream::connect(address).is_err());
    }
}
