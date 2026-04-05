package pro.uno.ui.card;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import pro.uno.R;

public class CardViewFactory {

    private final Context context;

    public CardViewFactory(Context context) {
        this.context = context;
    }

    public View createCardView(String data, ViewGroup parent, boolean large) {
        String[] parts = splitCard(data);
        boolean useNumberLayout = shouldUseNumberLayout(parts);

        View card = LayoutInflater.from(context).inflate(
                useNumberLayout ? R.layout.num_card_layout : R.layout.power_card_layout,
                parent,
                false
        );

        View cardBg = card.findViewById(R.id.card_bg);
        applyCardSize(cardBg, card, large);
        bindCardFace(card, parts, useNumberLayout, large);
        bindCardBackground(cardBg, safePart(parts, 0), safePart(parts, 1));

        if (large) {
            applyLargePreviewMargins(card, cardBg);
        }

        return card;
    }

    private String[] splitCard(String data) {
        if (data == null) {
            return new String[0];
        }
        return data.split("_");
    }

    // Power +2 cards are intentionally rendered as number cards with "+2" text.
    private boolean shouldUseNumberLayout(String[] parts) {
        String family = safePart(parts, 0);
        String action = safePart(parts, 2);
        return "card".equals(family) || ("power".equals(family) && "plus".equals(action));
    }

    private void applyCardSize(View cardBg, View card, boolean large) {
        int cardWidth = dpToPx(large ? 110 : 70);
        int cardHeight = dpToPx(large ? 150 : 90);
        ViewGroup.LayoutParams bgParams = cardBg.getLayoutParams();
        bgParams.width = cardWidth;
        bgParams.height = cardHeight;
        cardBg.setLayoutParams(bgParams);

        ViewGroup.LayoutParams cardParams = card.getLayoutParams();
        if (cardParams != null) {
            cardParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            cardParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            card.setLayoutParams(cardParams);
        }
    }

    private void bindCardFace(View card, String[] parts, boolean useNumberLayout, boolean large) {
        if (useNumberLayout) {
            bindNumberFace(card, parts, large);
            return;
        }
        bindPowerFace(card, parts, large);
    }

    private void bindNumberFace(View card, String[] parts, boolean large) {
        TextView numTl = card.findViewById(R.id.num_tl);
        TextView numCenter = card.findViewById(R.id.num);
        TextView numBr = card.findViewById(R.id.num_br);

        String family = safePart(parts, 0);
        String action = safePart(parts, 2);
        String value = ("power".equals(family) && "plus".equals(action))
                ? "+2"
                : safePart(parts, 2, "?");

        numTl.setText(value);
        numCenter.setText(value);
        numBr.setText(value);

        if (large) {
            setNumberTypography(numTl, numCenter, numBr, 20f, 40f, 20f, 14, 10);
        } else {
            setNumberTypography(numTl, numCenter, numBr, 14f, 22f, 14f, 10, 6);
        }
    }

    private void setNumberTypography(
            TextView numTl,
            TextView numCenter,
            TextView numBr,
            float cornerTextSize,
            float centerTextSize,
            float bottomTextSize,
            int horizontalPaddingDp,
            int verticalPaddingDp
    ) {
        numTl.setTextSize(cornerTextSize);
        numCenter.setTextSize(centerTextSize);
        numBr.setTextSize(bottomTextSize);
        numTl.setPadding(dpToPx(horizontalPaddingDp), dpToPx(verticalPaddingDp), 0, 0);
        numBr.setPadding(0, 0, dpToPx(horizontalPaddingDp), dpToPx(verticalPaddingDp));
    }

    private void bindPowerFace(View card, String[] parts, boolean large) {
        ImageView iconTl = card.findViewById(R.id.icon_tl);
        ImageView icon = card.findViewById(R.id.icon);
        ImageView iconBr = card.findViewById(R.id.icon_br);

        int iconRes = resolvePowerIcon(safePart(parts, 2));
        iconTl.setImageResource(iconRes);
        icon.setImageResource(iconRes);
        iconBr.setImageResource(iconRes);

        setPowerIconSize(iconTl, large ? 28 : 20, large ? 12 : 10, large ? 10 : 6, true);
        setPowerIconSize(iconBr, large ? 28 : 20, large ? 12 : 10, large ? 10 : 6, false);

        ViewGroup.LayoutParams centerParams = icon.getLayoutParams();
        centerParams.width = dpToPx(large ? 62 : 40);
        centerParams.height = dpToPx(large ? 62 : 40);
        icon.setLayoutParams(centerParams);

        int centerPadding = dpToPx(large ? 8 : 6);
        icon.setPadding(centerPadding, centerPadding, centerPadding, centerPadding);
    }

    private int resolvePowerIcon(String action) {
        if ("block".equals(action)) {
            return R.drawable.ic_block;
        }
        if ("plus".equals(action)) {
            return R.drawable.ic_token;
        }
        if ("color".equals(action)) {
            return R.drawable.ic_color;
        }
        return R.drawable.ic_reverse;
    }

    private void setPowerIconSize(
            ImageView iconView,
            int sizeDp,
            int sideMarginDp,
            int verticalMarginDp,
            boolean top
    ) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) iconView.getLayoutParams();
        params.width = dpToPx(sizeDp);
        params.height = dpToPx(sizeDp);
        if (top) {
            params.leftMargin = dpToPx(sideMarginDp);
            params.topMargin = dpToPx(verticalMarginDp);
            params.rightMargin = 0;
            params.bottomMargin = 0;
        } else {
            params.leftMargin = 0;
            params.topMargin = 0;
            params.rightMargin = dpToPx(sideMarginDp);
            params.bottomMargin = dpToPx(verticalMarginDp);
        }
        iconView.setLayoutParams(params);

        int iconPadding = dpToPx(sizeDp >= 28 ? 3 : 2);
        iconView.setPadding(iconPadding, iconPadding, iconPadding, iconPadding);
    }

    private void bindCardBackground(View cardBg, String family, String color) {
        int bgRes;
        if ("super".equals(family) || "black".equals(color)) {
            bgRes = R.drawable.card_unknown_bg;
        } else if ("red".equals(color)) {
            bgRes = R.drawable.card_red_bg;
        } else if ("blue".equals(color)) {
            bgRes = R.drawable.card_blue_bg;
        } else if ("green".equals(color)) {
            bgRes = R.drawable.card_green_bg;
        } else if ("yellow".equals(color)) {
            bgRes = R.drawable.card_yellow_bg;
        } else {
            bgRes = R.drawable.card_unknown_bg;
        }
        cardBg.setBackgroundResource(bgRes);
    }

    private void applyLargePreviewMargins(View card, View cardBg) {
        cardBg.setElevation(dpToPx(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = dpToPx(12);
        params.bottomMargin = dpToPx(18);
        card.setLayoutParams(params);
    }

    private String safePart(String[] parts, int index) {
        return safePart(parts, index, "");
    }

    private String safePart(String[] parts, int index, String fallback) {
        return (parts != null && index >= 0 && index < parts.length) ? parts[index] : fallback;
    }

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }
}
