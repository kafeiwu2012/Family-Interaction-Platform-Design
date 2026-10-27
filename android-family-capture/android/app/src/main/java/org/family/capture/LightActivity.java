package org.family.capture;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.*;
import android.widget.TextView;

/** Explicit content colors: do not rely on vendor-skinned window backgrounds. */
public class LightActivity extends Activity {
    static final int PAGE=0xfffafafa;
    private static final ColorStateList TEXT=new ColorStateList(
        new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},
        new int[]{0xff757575,0xff212121});
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setBackgroundDrawableResource(R.drawable.page_background);
    }
    @Override public void setContentView(View view){
        view.setBackgroundColor(PAGE);
        applyTextColors(view);
        super.setContentView(view);
    }
    static void applyTextColors(View view){
        if(view instanceof TextView){
            TextView text=(TextView)view;text.setTextColor(TEXT);text.setHintTextColor(0xff616161);
        }
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)applyTextColors(group.getChildAt(i));}
    }
}
