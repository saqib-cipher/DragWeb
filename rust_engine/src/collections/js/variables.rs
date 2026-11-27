use crate::models::{make_bean, BlockPalette};

pub struct Variables;

impl Variables {
    pub fn load() -> BlockPalette {
        let mut palette = BlockPalette::new("Variables", -1683200); // 0xFFE65100

        palette.push(make_bean("String", "setVarString", "set %var to %s", " ", -1683200, "%1$s = \"%2$s\";\n", vec!["title", "Welcome"]));
        palette.push(make_bean("Number", "setVarInt", "set %var to %d", " ", -1683200, "%1$s = %2$s;\n", vec!["count", "0"]));
        palette.push(make_bean("Boolean", "setVarBoolean", "set %var to %b", " ", -1683200, "%1$s = %2$s;\n", vec!["isOpen", "true"]));
        palette.push(make_bean("Get", "getVar", "%var", "s", -1683200, "%1$s", vec!["title"]));

        palette
    }
}

