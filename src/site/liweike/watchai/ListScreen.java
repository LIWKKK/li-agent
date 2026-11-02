package site.liweike.watchai;

import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/* JADX INFO: loaded from: classes.dex */
public abstract class ListScreen extends BaseActivity {
    protected LinearLayout col;
    private FrameLayout overlay;

    protected void startList(String str, String str2) {
        this.col = makeList(this.stage, str, str2, 52);
    }

    protected LinearLayout makeList(FrameLayout frameLayout, String str, String str2, int i) {
        EdgeScrollView edgeScrollView = new EdgeScrollView(this);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setGravity(1);
        linearLayout.setPadding(0, Ui.px(i), 0, Ui.px(120.0f));
        if (str != null) {
            TextView textViewText = Ui.text(this, str, 26.0f, -658195, "serif");
            textViewText.setSingleLine(true);
            textViewText.setEllipsize(TextUtils.TruncateAt.END);
            textViewText.setMaxWidth(Ui.px(280.0f));
            linearLayout.addView(textViewText, new LinearLayout.LayoutParams(-2, -2));
            if (str2 != null) {
                TextView textViewText2 = Ui.text(this, str2, 19.0f, -6514030, "sans-serif");
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
                layoutParams.topMargin = Ui.px(6.0f);
                linearLayout.addView(textViewText2, layoutParams);
            }
            linearLayout.addView(new View(this), new LinearLayout.LayoutParams(1, Ui.px(18.0f)));
        }
        edgeScrollView.addView(linearLayout, new FrameLayout.LayoutParams(-1, -2));
        frameLayout.addView(edgeScrollView, new FrameLayout.LayoutParams(-1, -1));
        return linearLayout;
    }

    protected LinearLayout makePlain(FrameLayout frameLayout, int i) {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setOverScrollMode(2);
        scrollView.setVerticalFadingEdgeEnabled(true);
        scrollView.setFadingEdgeLength(Ui.px(56.0f));
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setGravity(1);
        linearLayout.setPadding(0, Ui.px(i), 0, Ui.px(120.0f));
        scrollView.addView(linearLayout, new FrameLayout.LayoutParams(-1, -2));
        frameLayout.addView(scrollView, new FrameLayout.LayoutParams(-1, -1));
        return linearLayout;
    }

    protected View label(String str) {
        TextView textViewText = Ui.text(this, str, 18.0f, -6514030, "sans-serif-medium");
        textViewText.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(Ui.px(330.0f), -2);
        layoutParams.topMargin = Ui.px(6.0f);
        layoutParams.bottomMargin = Ui.px(10.0f);
        layoutParams.leftMargin = Ui.px(10.0f);
        textViewText.setLayoutParams(layoutParams);
        return textViewText;
    }

    protected View item(String str, String str2, int i, boolean z, View.OnClickListener onClickListener) {
        return item(str, str2, i, z, false, onClickListener);
    }

    protected View item(String str, String str2, int i, boolean z, boolean z2, View.OnClickListener onClickListener) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        linearLayout.setPadding(Ui.px(16.0f), Ui.px(12.0f), Ui.px(16.0f), Ui.px(12.0f));
        linearLayout.setBackground(z ? Ui.accent(28.0f) : Ui.card(28.0f));
        IconView iconView = new IconView(this, i, z ? -14737891 : -2525353);
        iconView.setPadding(Ui.px(9.0f), Ui.px(9.0f), Ui.px(9.0f), Ui.px(9.0f));
        iconView.setBackground(Ui.circle(z ? 637534208 : -13093325));
        linearLayout.addView(iconView, new LinearLayout.LayoutParams(Ui.px(42.0f), Ui.px(42.0f)));
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(1);
        linearLayout2.setGravity(16);
        TextView textViewText = Ui.text(this, str, 24.0f, z ? -14737891 : -658195, "serif");
        textViewText.setSingleLine(true);
        textViewText.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout2.addView(textViewText);
        if (str2 != null) {
            linearLayout2.addView(new View(this), new LinearLayout.LayoutParams(1, Ui.px(2.0f)));
            TextView textViewText2 = Ui.text(this, str2, 18.0f, z ? -1289806307 : -6514030, "sans-serif");
            textViewText2.setSingleLine(true);
            textViewText2.setEllipsize(TextUtils.TruncateAt.END);
            linearLayout2.addView(textViewText2);
        }
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
        layoutParams.leftMargin = Ui.px(12.0f);
        linearLayout.addView(linearLayout2, layoutParams);
        if (z2) {
            View iconView2 = new IconView(this, 10, -2525353);
            LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(Ui.px(24.0f), Ui.px(24.0f));
            layoutParams2.leftMargin = Ui.px(8.0f);
            linearLayout.addView(iconView2, layoutParams2);
        }
        LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(Ui.px(330.0f), -2);
        layoutParams3.bottomMargin = Ui.px(10.0f);
        linearLayout.setLayoutParams(layoutParams3);
        linearLayout.setClickable(true);
        linearLayout.setOnClickListener(onClickListener);
        Ui.pressable(linearLayout);
        return linearLayout;
    }

    protected View empty(String str) {
        TextView textViewText = Ui.text(this, str, 20.0f, -6514030, "sans-serif");
        textViewText.setGravity(17);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(Ui.px(330.0f), -2);
        layoutParams.topMargin = Ui.px(24.0f);
        textViewText.setLayoutParams(layoutParams);
        return textViewText;
    }

    protected void closeOverlay() {
        if (this.overlay != null) {
            this.stage.removeView(this.overlay);
            this.overlay = null;
        }
    }

    @Override // site.liweike.watchai.BaseActivity
    protected void handleBack() {
        if (this.overlay == null) {
            finish();
        } else {
            closeOverlay();
        }
    }

    protected void confirm(String str, String str2, String str3, String str4, final Runnable runnable) {
        closeOverlay();
        this.overlay = new FrameLayout(this);
        this.overlay.setBackgroundColor(-434892014);
        this.overlay.setClickable(true);
        this.overlay.setOnClickListener(new View.OnClickListener() { // from class: site.liweike.watchai.ListScreen.1
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                ListScreen.this.closeOverlay();
            }
        });
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setGravity(1);
        linearLayout.setPadding(Ui.px(20.0f), Ui.px(22.0f), Ui.px(20.0f), Ui.px(20.0f));
        linearLayout.setBackground(Ui.card(34.0f));
        linearLayout.setClickable(true);
        TextView textViewText = Ui.text(this, str, 26.0f, -658195, "serif");
        textViewText.setGravity(17);
        linearLayout.addView(textViewText, new LinearLayout.LayoutParams(-1, -2));
        TextView textViewText2 = Ui.text(this, str2, 20.0f, -658195, "sans-serif");
        textViewText2.setGravity(17);
        textViewText2.setMaxLines(2);
        textViewText2.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.topMargin = Ui.px(8.0f);
        linearLayout.addView(textViewText2, layoutParams);
        TextView textViewText3 = Ui.text(this, str3, 17.0f, -6514030, "sans-serif");
        textViewText3.setGravity(17);
        textViewText3.setMaxLines(2);
        LinearLayout.LayoutParams layoutParams2 = new LinearLayout.LayoutParams(-1, -2);
        layoutParams2.topMargin = Ui.px(6.0f);
        layoutParams2.bottomMargin = Ui.px(16.0f);
        linearLayout.addView(textViewText3, layoutParams2);
        LinearLayout linearLayout2 = new LinearLayout(this);
        linearLayout2.setOrientation(0);
        TextView textViewText4 = Ui.text(this, "取消", 22.0f, -658195, "sans-serif-medium");
        textViewText4.setGravity(17);
        textViewText4.setBackground(Ui.circle(-13093325));
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(-13093325);
        gradientDrawable.setCornerRadius(Ui.px(28.0f));
        textViewText4.setBackground(gradientDrawable);
        textViewText4.setClickable(true);
        textViewText4.setOnClickListener(new View.OnClickListener() { // from class: site.liweike.watchai.ListScreen.2
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                ListScreen.this.closeOverlay();
            }
        });
        Ui.pressable(textViewText4);
        LinearLayout.LayoutParams layoutParams3 = new LinearLayout.LayoutParams(0, Ui.px(54.0f), 1.0f);
        layoutParams3.rightMargin = Ui.px(6.0f);
        linearLayout2.addView(textViewText4, layoutParams3);
        TextView textViewText5 = Ui.text(this, str4, 22.0f, -14737891, "sans-serif-medium");
        textViewText5.setGravity(17);
        textViewText5.setBackground(Ui.accent(28.0f));
        textViewText5.setClickable(true);
        textViewText5.setOnClickListener(new View.OnClickListener() { // from class: site.liweike.watchai.ListScreen.3
            @Override // android.view.View.OnClickListener
            public void onClick(View view) {
                ListScreen.this.closeOverlay();
                runnable.run();
            }
        });
        Ui.pressable(textViewText5);
        LinearLayout.LayoutParams layoutParams4 = new LinearLayout.LayoutParams(0, Ui.px(54.0f), 1.0f);
        layoutParams4.leftMargin = Ui.px(6.0f);
        linearLayout2.addView(textViewText5, layoutParams4);
        linearLayout.addView(linearLayout2, new LinearLayout.LayoutParams(-1, -2));
        this.overlay.addView(linearLayout, Ui.fl(318.0f, -2.0f, 17, 0.0f, 0.0f));
        this.stage.addView(this.overlay, new FrameLayout.LayoutParams(-1, -1));
        this.overlay.setAlpha(0.0f);
        this.overlay.animate().alpha(1.0f).setDuration(140L).start();
    }

    protected LinearLayout card() {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(Ui.px(18.0f), Ui.px(16.0f), Ui.px(18.0f), Ui.px(8.0f));
        linearLayout.setBackground(Ui.card(28.0f));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(Ui.px(330.0f), -2);
        layoutParams.bottomMargin = Ui.px(12.0f);
        linearLayout.setLayoutParams(layoutParams);
        return linearLayout;
    }

    protected View kv(String str, String str2, int i) {
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(0);
        linearLayout.setGravity(16);
        TextView textViewText = Ui.text(this, str, 19.0f, -6514030, "sans-serif");
        textViewText.setSingleLine(true);
        textViewText.setEllipsize(TextUtils.TruncateAt.END);
        linearLayout.addView(textViewText, new LinearLayout.LayoutParams(Ui.px(i), -2));
        TextView textViewText2 = Ui.text(this, str2, 21.0f, -658195, "sans-serif");
        textViewText2.setSingleLine(true);
        textViewText2.setEllipsize(TextUtils.TruncateAt.END);
        textViewText2.setGravity(8388613);
        linearLayout.addView(textViewText2, new LinearLayout.LayoutParams(0, -2, 1.0f));
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -2);
        layoutParams.bottomMargin = Ui.px(10.0f);
        linearLayout.setLayoutParams(layoutParams);
        return linearLayout;
    }
}
