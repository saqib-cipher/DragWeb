use crate::models::{make_bean, BlockPalette};

pub struct DirectCss;

impl DirectCss {
    pub fn load() -> BlockPalette {
        let mut palette = BlockPalette::new("Direct CSS", -13158097); // 0xFF37474F

        palette.push(make_bean("Raw Source", "css_direct_code", "add CSS: %s.inputOnly", "s", -13158097, "%1$s", vec![".custom { color: red; }"]));

        palette
    }
}

