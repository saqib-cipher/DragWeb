use crate::models::block_bean::{parse_spec_param_types, DragWebBlockBean};
use serde_json::{json, Value};

pub struct BlockPalette {
    pub name: String,
    pub color: i32,
    pub beans: Vec<DragWebBlockBean>,
}

impl BlockPalette {
    pub fn new(name: &str, color: i32) -> Self {
        Self {
            name: name.to_string(),
            color,
            beans: Vec::new(),
        }
    }

    pub fn push(&mut self, mut bean: DragWebBlockBean) {
        if bean.color == 0 || bean.color == -10453621 {
            bean.color = self.color;
        }
        if bean.category.is_empty() {
            bean.category = self.name.clone();
        }
        self.beans.push(bean);
    }

    pub fn to_json(&self) -> Value {
        json!({
            "name": self.name,
            "color": self.color,
            "beans": self.beans.iter().map(|b| {
                json!({
                    "header_text": b.header_text,
                    "op_code": b.op_code,
                    "spec": b.spec,
                    "spec2": b.spec2,
                    "category": b.category,
                    "block_type": b.block_type,
                    "color": b.color,
                    "code": b.code,
                    "tokenized_spec": b.tokenized_spec,
                    "parameters": b.parameters
                })
            }).collect::<Vec<_>>()
        })
    }
}

pub fn make_bean_spec2(
    header: &str,
    op_code: &str,
    spec: &str,
    spec2: &str,
    btype: &str,
    color: i32,
    code: &str,
    params: Vec<&str>,
) -> DragWebBlockBean {
    let mut bean = make_bean(header, op_code, spec, btype, color, code, params);
    bean.spec2 = spec2.to_string();
    bean
}

pub fn make_bean(
    header: &str,
    op_code: &str,
    spec: &str,
    btype: &str,
    color: i32,
    code: &str,
    params: Vec<&str>,
) -> DragWebBlockBean {
    DragWebBlockBean {
        id: "-1".to_string(),
        op_code: op_code.to_string(),
        spec: spec.to_string(),
        spec2: String::new(),
        block_type: btype.to_string(),
        category: String::new(),
        color,
        code: code.to_string(),
        parameters: params.into_iter().map(|s| s.to_string()).collect(),
        param_types: parse_spec_param_types(spec),
        sub_stack1: -1,
        sub_stack2: -1,
        next_block: -1,
        stack_index: -1,
        is_connected_to_root: false,
        x: 0,
        y: 0,
        header_text: header.to_string(),
        tokenized_spec: parse_spec_param_types(spec),
    }
}

