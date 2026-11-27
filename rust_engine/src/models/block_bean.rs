use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct DragWebBlockBean {
    pub id: String,
    #[serde(rename = "opCode", alias = "op_code")]
    pub op_code: String,
    pub spec: String,
    #[serde(default)]
    pub spec2: String,
    #[serde(rename = "type", alias = "block_type")]
    pub block_type: String,
    #[serde(default)]
    pub category: String,
    pub color: i32,
    #[serde(default)]
    pub code: String,
    #[serde(default)]
    pub parameters: Vec<String>,
    #[serde(rename = "paramTypes", alias = "param_types", default)]
    pub param_types: Vec<String>,
    #[serde(rename = "subStack1", alias = "sub_stack1")]
    pub sub_stack1: i32,
    #[serde(rename = "subStack2", alias = "sub_stack2")]
    pub sub_stack2: i32,
    #[serde(rename = "nextBlock", alias = "next_block")]
    pub next_block: i32,
    #[serde(rename = "stackIndex", alias = "stack_index", default)]
    pub stack_index: i32,
    #[serde(rename = "isConnectedToRoot", alias = "is_connected_to_root", default)]
    pub is_connected_to_root: bool,
    #[serde(default)]
    pub x: i32,
    #[serde(default)]
    pub y: i32,
    #[serde(rename = "headerText", alias = "header_text", default)]
    pub header_text: String,
    #[serde(rename = "tokenizedSpec", alias = "tokenized_spec", default)]
    pub tokenized_spec: Vec<String>,
}

impl Default for DragWebBlockBean {
    fn default() -> Self {
        Self {
            id: "-1".to_string(),
            op_code: String::new(),
            spec: String::new(),
            spec2: String::new(),
            block_type: " ".to_string(),
            category: String::new(),
            color: -10453621,
            code: String::new(),
            parameters: Vec::new(),
            param_types: Vec::new(),
            sub_stack1: -1,
            sub_stack2: -1,
            next_block: -1,
            stack_index: -1,
            is_connected_to_root: false,
            x: 0,
            y: 0,
            header_text: String::new(),
            tokenized_spec: Vec::new(),
        }
    }
}

pub fn parse_spec_param_types(spec: &str) -> Vec<String> {
    let mut types = Vec::new();
    let chars: Vec<char> = spec.chars().collect();
    let mut i = 0;
    while i < chars.len() {
        if chars[i] == '%' && i + 1 < chars.len() {
            i += 1;
            let mut token = String::new();
            while i < chars.len() && chars[i] != ' ' && chars[i] != '<' && chars[i] != '>' && chars[i] != '"' && chars[i] != '\'' && chars[i] != '/' {
                token.push(chars[i]);
                i += 1;
            }
            let token = token.trim_end_matches(&[',', ';', ')', '(', ']', '['][..]);
            if !token.is_empty() {
                types.push(token.to_string());
            }
        } else {
            i += 1;
        }
    }
    types
}

pub fn parse_hex_color_i32(hex: &str) -> i32 {
    let s = hex.trim_start_matches('#');
    if s.len() == 6 {
        u32::from_str_radix(s, 16)
            .map(|rgb| (0xFF000000 | rgb) as i32)
            .unwrap_or(-10453621)
    } else if s.len() == 8 {
        u32::from_str_radix(s, 16)
            .map(|rgba| rgba as i32)
            .unwrap_or(-10453621)
    } else {
        -10453621
    }
}

