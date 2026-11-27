use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use serde_json::json;

use crate::collections::load_all_langs_json;
use crate::generators::{
    css::generate_css_blocks,
    html::{generate_dragweb_blocks, generate_html_from_blocks},
    js::generate_js_blocks,
};

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_glab_dragweb_core_BlocksLoader_nativeGenerateHtmlBlocks(
    mut env: JNIEnv,
    _class: JClass,
    html_code: JString,
    blocks_json: JString,
) -> jstring {
    let binding_code = match env.get_string(&html_code) {
        Ok(s) => s,
        Err(e) => {
            let err_json = json!({ "Error": format!("Failed to read html string: {:?}", e) }).to_string();
            return env.new_string(err_json).unwrap().into_raw();
        }
    };
    let code_str = match binding_code.to_str() {
        Ok(s) => s,
        Err(e) => {
            let err_json = json!({ "Error": format!("Invalid UTF-8 in html: {:?}", e) }).to_string();
            return env.new_string(err_json).unwrap().into_raw();
        }
    };

    let binding_json = env.get_string(&blocks_json).ok();
    let json_str = binding_json.as_ref().and_then(|s| s.to_str().ok()).unwrap_or("");

    let blocks = generate_dragweb_blocks(code_str, json_str);
    let json_result = serde_json::to_string(&blocks).unwrap_or_else(|_| "[]".to_string());

    env.new_string(json_result).unwrap().into_raw()
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_glab_dragweb_core_BlocksLoader_nativeGenerateCssBlocks(
    mut env: JNIEnv,
    _class: JClass,
    css_code: JString,
    blocks_json: JString,
) -> jstring {
    let binding_code = match env.get_string(&css_code) {
        Ok(s) => s,
        Err(e) => {
            let err_json = json!({ "Error": format!("Failed to read css string: {:?}", e) }).to_string();
            return env.new_string(err_json).unwrap().into_raw();
        }
    };
    let code_str = match binding_code.to_str() {
        Ok(s) => s,
        Err(e) => {
            let err_json = json!({ "Error": format!("Invalid UTF-8 in css: {:?}", e) }).to_string();
            return env.new_string(err_json).unwrap().into_raw();
        }
    };

    let binding_json = env.get_string(&blocks_json).ok();
    let json_str = binding_json.as_ref().and_then(|s| s.to_str().ok()).unwrap_or("");

    let blocks = generate_css_blocks(code_str, json_str);
    let json_result = serde_json::to_string(&blocks).unwrap_or_else(|_| "[]".to_string());

    env.new_string(json_result).unwrap().into_raw()
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_glab_dragweb_core_BlocksLoader_nativeGenerateJsBlocks(
    mut env: JNIEnv,
    _class: JClass,
    js_code: JString,
    blocks_json: JString,
) -> jstring {
    let binding_code = match env.get_string(&js_code) {
        Ok(s) => s,
        Err(e) => {
            let err_json = json!({ "Error": format!("Failed to read js string: {:?}", e) }).to_string();
            return env.new_string(err_json).unwrap().into_raw();
        }
    };
    let code_str = match binding_code.to_str() {
        Ok(s) => s,
        Err(e) => {
            let err_json = json!({ "Error": format!("Invalid UTF-8 in js: {:?}", e) }).to_string();
            return env.new_string(err_json).unwrap().into_raw();
        }
    };

    let binding_json = env.get_string(&blocks_json).ok();
    let json_str = binding_json.as_ref().and_then(|s| s.to_str().ok()).unwrap_or("");

    let blocks = generate_js_blocks(code_str, json_str);
    let json_result = serde_json::to_string(&blocks).unwrap_or_else(|_| "[]".to_string());

    env.new_string(json_result).unwrap().into_raw()
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_glab_dragweb_core_BlocksLoader_nativeGenerateHtmlFromBlocks(
    mut env: JNIEnv,
    _class: JClass,
    blocks_json: JString,
) -> jstring {
    let binding = match env.get_string(&blocks_json) {
        Ok(s) => s,
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };
    let json_str = binding.to_str().unwrap_or("");
    let html_result = generate_html_from_blocks(json_str);
    env.new_string(html_result).unwrap().into_raw()
}

#[allow(non_snake_case)]
#[no_mangle]
pub extern "system" fn Java_glab_dragweb_core_BlocksLoader_nativeLoadBlocksCollection(
    env: JNIEnv,
    _class: JClass,
) -> jstring {
    let collection = load_all_langs_json();
    let json_str = serde_json::to_string(&collection).unwrap_or_else(|_| "{}".to_string());
    env.new_string(json_str).unwrap().into_raw()
}

