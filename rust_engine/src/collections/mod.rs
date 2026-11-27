pub mod css;
pub mod html;
pub mod js;

use crate::models::BlockPalette;
use serde_json::{Map, Value};

pub fn load_lang(language: &str) -> Vec<BlockPalette> {
    match language.to_ascii_lowercase().as_str() {
        "html" | "xml" => html::load(),
        "css" => css::load(),
        "js" | "javascript" => js::load(),
        _ => Vec::new(),
    }
}

pub fn load_all_langs_json() -> Value {
    let mut obj = Map::new();
    let langs = ["html", "css", "js"];

    for l in langs {
        let palettes = load_lang(l);
        let arr: Vec<Value> = palettes.iter().map(|p| p.to_json()).collect();
        obj.insert(l.to_string(), Value::Array(arr));
    }

    Value::Object(obj)
}

