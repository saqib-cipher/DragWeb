use crate::models::{make_bean, BlockPalette};

pub struct DirectHtml;

impl DirectHtml {
    pub fn load() -> BlockPalette {
        let mut palette = BlockPalette::new("Direct HTML", -13158097); // 0xFF37474F

        palette.push(make_bean("Raw Source", "html_direct_code", "add HTML: %s.inputOnly", " ", -13158097, "%1$s", vec!["<div></div>"]));

        palette
    }
}

