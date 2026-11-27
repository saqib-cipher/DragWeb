use crate::models::{make_bean, BlockPalette};

pub struct DirectJs;

impl DirectJs {
    pub fn load() -> BlockPalette {
        let mut palette = BlockPalette::new("Direct JS", -13158097); // 0xFF37474F

        palette.push(make_bean("Raw Source", "addSourceCode", "add source directly %s.inputOnly", "s", -13158097, "%1$s;\n", vec![""]));

        palette
    }
}

