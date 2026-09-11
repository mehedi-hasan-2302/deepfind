#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod backend;
#[cfg(windows)]
mod job;

use backend::Backend;
use std::{
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc, Mutex,
    },
    thread,
    time::Duration,
};
use tauri::{webview::NewWindowResponse, Manager, RunEvent, WebviewUrl, WebviewWindowBuilder};

fn main() {
    let backend = Arc::new(Backend::default());
    let closing = Arc::new(AtomicBool::new(false));
    let finished = Arc::new(AtomicBool::new(false));
    let worker_backend = backend.clone();
    let worker_closing = closing.clone();
    let app = tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _, _| {
            if let Some(window) = app.get_webview_window("main") {
                let _ = window.unminimize();
                let _ = window.show();
                let _ = window.set_focus();
            }
        }))
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { api, .. } = event {
                api.prevent_close();
                window.app_handle().exit(0);
            }
        })
        .setup(move |app| {
            let port = Arc::new(Mutex::new(None));
            let navigation_port = port.clone();
            let window =
                WebviewWindowBuilder::new(app, "main", WebviewUrl::App("index.html".into()))
                    .title("DeepFind")
                    .inner_size(1200.0, 820.0)
                    .min_inner_size(720.0, 560.0)
                    .on_navigation(move |url| {
                        backend::allowed_navigation(
                            url,
                            navigation_port.lock().ok().and_then(|p| *p),
                        )
                    })
                    .on_new_window(|_, _| NewWindowResponse::Deny)
                    .on_download(|_, _| false)
                    .build()?;
            let local_origin = window.url()?;
            let recovery = local_origin.join("recovery.html")?;
            let app_handle = app.handle().clone();
            thread::spawn(move || {
                match worker_backend.start(&app_handle) {
                    Ok(actual_port) if !worker_closing.load(Ordering::SeqCst) => {
                        *port.lock().expect("navigation port") = Some(actual_port);
                        let url = format!("http://127.0.0.1:{actual_port}/")
                            .parse()
                            .expect("loopback URL");
                        if window.navigate(url).is_ok() {
                            while !worker_closing.load(Ordering::SeqCst) && worker_backend.running()
                            {
                                thread::sleep(Duration::from_millis(500));
                            }
                        }
                    }
                    _ => {}
                }
                if !worker_closing.load(Ordering::SeqCst) {
                    let _ = window.navigate(recovery);
                    let _ = window.set_title("DeepFind — Local engine unavailable");
                }
                worker_backend.stop();
            });
            Ok(())
        })
        .build(tauri::generate_context!())
        .expect("DeepFind could not initialize its desktop window");
    app.run(move |app, event| {
        if let RunEvent::ExitRequested { api, .. } = event {
            if finished.load(Ordering::SeqCst) {
                return;
            }
            api.prevent_exit();
            if !closing.swap(true, Ordering::SeqCst) {
                if let Some(window) = app.get_webview_window("main") {
                    let _ = window.set_title("DeepFind — Closing");
                    // Navigation to a bundled page remains allowed even after the backend exits.
                    let _ = window.navigate(
                        "http://tauri.localhost/closing.html"
                            .parse()
                            .expect("local URL"),
                    );
                }
                let backend = backend.clone();
                let finished = finished.clone();
                let app = app.clone();
                thread::spawn(move || {
                    backend.stop();
                    finished.store(true, Ordering::SeqCst);
                    app.exit(0);
                });
            }
        }
    });
}
