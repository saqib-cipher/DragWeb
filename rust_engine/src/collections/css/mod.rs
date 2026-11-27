pub mod appearance_effects;
pub mod direct_css;
pub mod layout_box;
pub mod selectors;
pub mod typography;

use crate::models::BlockPalette;

pub fn load() -> Vec<BlockPalette> {
    vec![
        selectors::Selectors::load(),
        layout_box::LayoutBox::load(),
        typography::Typography::load(),
        appearance_effects::AppearanceEffects::load(),
        direct_css::DirectCss::load(),
    ]
}

