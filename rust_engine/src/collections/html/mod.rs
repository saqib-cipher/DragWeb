pub mod direct_html;
pub mod identity_state;
pub mod layout_dim;
pub mod operators;
pub mod style_appearance;
pub mod text_typography;
pub mod views;

use crate::models::BlockPalette;

pub fn load() -> Vec<BlockPalette> {
    vec![
        views::Views::load(),
        operators::Operators::load(),
        layout_dim::LayoutDimensions::load(),
        text_typography::TextTypography::load(),
        style_appearance::StyleAppearance::load(),
        identity_state::IdentityState::load(),
        direct_html::DirectHtml::load(),
    ]
}

