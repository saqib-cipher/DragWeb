use crate::models::{make_bean, BlockPalette};

pub struct Operators;

impl Operators {
    pub fn load() -> BlockPalette {
        let mut palette = BlockPalette::new("Operators", -16718218); // 0xFF00E676

        palette.push(make_bean("Comments & Doc", "html_comment", "<!-- %s -->", " ", -16718218, "<!-- %1$s -->", vec![""]));
        palette.push(make_bean("", "html_doctype", "<!DOCTYPE html>", " ", -16718218, "<!DOCTYPE html>", vec![]));

        palette
    }
}

