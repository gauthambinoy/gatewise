// Prevent a console window from popping up alongside the app on Windows in release
// builds. In debug we keep the console so logging is visible.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    gatewise_desktop_lib::run()
}
