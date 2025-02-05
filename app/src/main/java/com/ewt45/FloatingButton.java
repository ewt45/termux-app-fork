package com.ewt45;

import static android.content.pm.ApplicationInfo.FLAG_TEST_ONLY;
import static android.graphics.PixelFormat.RGBA_8888;
import static android.graphics.drawable.GradientDrawable.OVAL;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_PANEL;

import static androidx.constraintlayout.widget.ConstraintSet.PARENT_ID;
import static androidx.constraintlayout.widget.ConstraintSet.START;
import static androidx.constraintlayout.widget.ConstraintSet.TOP;
import static androidx.core.content.pm.ShortcutManagerCompat.FLAG_MATCH_CACHED;
import static androidx.core.content.pm.ShortcutManagerCompat.FLAG_MATCH_DYNAMIC;
import static androidx.core.content.pm.ShortcutManagerCompat.FLAG_MATCH_MANIFEST;
import static androidx.core.content.pm.ShortcutManagerCompat.FLAG_MATCH_PINNED;

import static java.lang.Math.pow;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxApplication;
import com.termux.shared.view.ViewUtils;
import com.termux.x11.LoriePreferences;
import com.termux.x11.MainActivity;

import java.util.List;

public class FloatingButton implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "FloatingButton";
    private static boolean isTest;
    private ConstraintLayout frame;
    //    private final ImageButton btn;
    private ImageButton[] btns;
    //记录activity用于切换活动时调用startActivity. onResumed时存储 onPause时清空
    MainActivity tX11Activity = null;
    TermuxActivity tAppActivity = null;
    @ACTIVE_ACTIVITY int currentActivity = ACTIVITY_OTHER;

    /**
     * TermuxApplication.onCreate()时调用。注册活动的生命周期监听，以在对应活动显示悬浮球
     */
    public static void init(TermuxApplication application) {
        isTest = (application.getApplicationInfo().flags & FLAG_TEST_ONLY) != 0;
        createX11SettingsShortcutIfNeeded(application);
        application.registerActivityLifecycleCallbacks(new FloatingButton(application));
    }

    @SuppressLint("ClickableViewAccessibility")
    public FloatingButton(Application c) {
        frame = new ConstraintLayout(c);

        btns = new ImageButton[] {
            //主按钮
            //com.termux.shared.R.drawable.ic_settings

            createOneButton(c, com.termux.x11.R.drawable.ic_x11_icon, null),
            //切换至x11
            createOneButton(c, com.termux.x11.R.drawable.ic_x11_icon, v -> {
                if (tAppActivity != null && currentActivity == ACTIVITY_TAPP)
                    tAppActivity.startActivity(new Intent(tAppActivity, com.termux.x11.MainActivity.class));
            }),
            //切换至终端
            createOneButton(c, com.termux.R.drawable.ic_service_notification, v -> {
                if (tX11Activity != null && currentActivity == ACTIVITY_TX11)
                    tX11Activity.startActivity(new Intent(tX11Activity, TermuxActivity.class));
            }),
            //启动xserver
            createOneButton(c, 0, v -> {
                Activity a = tAppActivity != null ? tAppActivity : tX11Activity;
                if (a != null)
                    a.startService(new Intent(a, X11ServiceTest.class));
            })
        };

        final int dp8 = (int) ViewUtils.dpToPx(c, 8);

        final int maxSubBtnOneCircle = 3;
        final int btnSize = dp8*5;
        final int firstCircleRadius = dp8*8;
        final int frameSizedFolded = btnSize;
        final int frameSizeUnFolded = btnSize + btns.length/maxSubBtnOneCircle * firstCircleRadius;

        ValueAnimator animator = ValueAnimator.ofFloat(0,1);
        animator.setDuration(200);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation, boolean reverse) {
                super.onAnimationStart(animation);
                if (!reverse) {
                    WindowManager.LayoutParams params = (WindowManager.LayoutParams) frame.getLayoutParams();
                    params.width = frameSizeUnFolded;
                    params.height = frameSizeUnFolded;
                    frame.getContext().getSystemService(WindowManager.class).updateViewLayout(frame, params);
                }
            }

            @Override
            public void onAnimationEnd(Animator animation, boolean isReverse) {
                super.onAnimationEnd(animation, isReverse);
                if(isReverse) {
                    WindowManager.LayoutParams params = (WindowManager.LayoutParams) frame.getLayoutParams();
                    params.width = frameSizedFolded;
                    params.height = frameSizedFolded;
                    frame.getContext().getSystemService(WindowManager.class).updateViewLayout(frame, params);
                }
            }
        });
        animator.addUpdateListener(animation -> {
            float value = (float)animation.getAnimatedValue();
            for(int i=1; i<btns.length; i++) {
                ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) btns[i].getLayoutParams();
//                lp.circleAngle = 360f / (btns.length - 1) * i /*原始角度*/ + /*加过渡角度*/ 120 * (1 - value);
//                lp.circleRadius = (int) (firstCircleRadius * value);
                lp.circleRadius = (int) (firstCircleRadius * (1 + (i - 1) / maxSubBtnOneCircle) * value);
                btns[i].setLayoutParams(lp);
            }
        });

        //单击切换x11,双击隐藏
        GestureDetector gestureDetector = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener(){
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                removeFrameFromWindow(tAppActivity != null ? tAppActivity : tX11Activity);
                if (tAppActivity != null && currentActivity == ACTIVITY_TAPP){
                    btns[0].setImageResource(R.drawable.ic_service_notification);
                    tAppActivity.startActivity(new Intent(tAppActivity, com.termux.x11.MainActivity.class));
                }
                else if (tX11Activity != null && currentActivity == ACTIVITY_TX11) {
                    btns[0].setImageResource(com.termux.x11.R.drawable.ic_x11_icon);
                    tX11Activity.startActivity(new Intent(tX11Activity, TermuxActivity.class));
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                removeFrameFromWindow(tAppActivity != null ? tAppActivity : tX11Activity);
                return true;
            }
        });

        //悬浮球可移动
        makeMoveable(btns[0], frame, (v, ev) -> gestureDetector.onTouchEvent(ev));

//        btns[0].setOnClickListener(v -> {
//            //如果frame宽高设置为wrap，那么在展开时会随着radius变大，以左上角为原点，宽高逐渐变大，导致中心按钮的位置不断改变。
//            //我现在希望中心按钮保持不动。所以需要在动画开始前，瞬间以中心为原点扩充frame宽高，保证动画播放时不会宽高再变化
//
//            //这样改了之后，窗口修改lp之后，中心按钮会从右下移动到中心，有个可见的移动过程。推测是哪个地方开启动画了。
//            /*
//            - frame.getLayoutxxx 查看动画相关，都是null。
//            - frame的lp.animationxxx 查看WindowManager的动画相关，也都是null或0
//
//            经过检查，发现按钮始终居中。frame宽高立刻变大，左上角xy位置缓缓移动。
//            gravity设置左上角，修改宽高会立刻移动。设置居中，修改宽高，先以左上角为原点，宽高立刻达到修改值，整体位置移动会有动画效果。
//            放弃. 中心按钮改为左上按钮。这样frame只改宽高就行了。
//             */
//            boolean isFolded = ((ConstraintLayout.LayoutParams)btns[1].getLayoutParams()).circleRadius == 0;
//            if(isFolded)
//                animator.start();
//            else
//                animator.reverse();
//        });

        btns[0].setElevation(5);

//        if(isTest) frame.setBackgroundColor(Color.BLUE);
        WindowManager.LayoutParams frameParams = new WindowManager.LayoutParams(
            frameSizedFolded, frameSizedFolded, TYPE_APPLICATION_PANEL, FLAG_NOT_TOUCH_MODAL | FLAG_NOT_FOCUSABLE, RGBA_8888);
//        frameParams.gravity = Gravity.CENTER;
        frameParams.gravity = Gravity.START | Gravity.TOP;
        frameParams.windowAnimations = 0;
        frame.setLayoutParams(frameParams);
        frame.setFocusable(false);

        ConstraintSet set = new ConstraintSet();
        set.connect(btns[0].getId(), START, PARENT_ID, START);
        set.connect(btns[0].getId(), TOP, PARENT_ID, TOP);

        for(int i=0; i<btns.length; i++) {
            frame.addView(btns[i]);
            set.constrainHeight(btns[i].getId(), btnSize);
            set.constrainWidth(btns[i].getId(), btnSize);
            if (i != 0)
                set.constrainCircle(btns[i].getId(), btns[0].getId(), 0, 90 + 90f / (maxSubBtnOneCircle - 1) * ((i-1) % maxSubBtnOneCircle));
        }

        set.applyTo(frame);

//        set.clone(c, R.layout.menu);
//        set.getHeight(0);
    }

    private void removeFrameFromWindow(Activity a) {
        WindowManager windowManager = a.getWindowManager();
        try {
            if(frame.getParent() != null)
                windowManager.removeView(frame);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static ImageButton createOneButton(Context c, int drawable, View.OnClickListener listener) {
        ImageButton btn = new ImageButton(c);
        btn.setId(View.generateViewId());
        btn.setImageResource(drawable);
        btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btn.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        btn.setOnClickListener(listener);
        GradientDrawable bgDrawable = new GradientDrawable();
        bgDrawable.setShape(OVAL);
        bgDrawable.setColor(Color.GRAY);
        btn.setBackground(bgDrawable);
        return btn;
    }

    private void test(){
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        /*
        如果进入app或x11活动，则显示。
        否则，如果进入的是其他活动，按钮隐藏。

        因为复用了按钮视图实例，所以需要在第二次添加前，保证视图已经被从窗口移除。

        悬浮窗类型选择TYPE_APPLICATION_PANEL时，不需申请额外权限，无法显示到其他应用上。
        需要window的token（一个IBinder)，但是在onResmue中获取的token为null，所以延迟1秒再获取。
        此token貌似只能从decorView获取，windowManager.LayoutParams获取的不行。

        窗口泄露的原因：在activity退出之后仍然没有移除窗口
        1. 移除窗口时机不对，有时候活动退出仍未调用移除窗口代码。
            - 解决办法：在onPause回调中移除窗口。
        2. 一秒内切了两次活动，导致一秒后将窗口添加到了已经退出的活动上。
            - 解决办法：在onResume回调中记录当前显示的活动。一秒后将最新显示的活动与一秒前显示的活动对比，若不等则跳过添加窗口。
         */

        final boolean shouldShowBtn;
        if (activity instanceof TermuxActivity) {
            shouldShowBtn = true;
            tAppActivity = (TermuxActivity) activity;
            currentActivity = ACTIVITY_TAPP;
        } else if (activity instanceof MainActivity) {
            shouldShowBtn = true;
            tX11Activity = (MainActivity) activity;
            currentActivity = ACTIVITY_TX11;
        } else {
            shouldShowBtn = false;
            currentActivity = ACTIVITY_OTHER;
        }

        if (shouldShowBtn) {
            final int activityWhenPost = currentActivity;
            new Handler().postDelayed(() -> {
                //如果这个activity只显示了不到一秒就切成别的了，不应该再显示。
                //否则会有WindowLeaked报错
                if (activityWhenPost != currentActivity)
                    return;
                try {
                    Activity a = activityWhenPost == ACTIVITY_TAPP ? tAppActivity : tX11Activity;
                    WindowManager.LayoutParams params = (WindowManager.LayoutParams) frame.getLayoutParams();
                    params.token = a.getWindow().getDecorView().getWindowToken(); // 必须要
                    WindowManager windowManager = a.getWindowManager();
                    windowManager.addView(frame, params);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 1000);
        }
    }


    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        //是否需要在这里处理按钮隐藏？如果处理，是否要考虑旧活动paused在新活动resumed之后被执行 导致切换活动后按钮显示一下又被隐藏的情况？
        if (activity instanceof TermuxActivity) {
            tAppActivity = null;
            removeFrameFromWindow(activity);
        } else if (activity instanceof MainActivity) {
            tX11Activity = null;
            removeFrameFromWindow(activity);
        }
        currentActivity = ACTIVITY_OTHER;
    }


    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {

    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {

    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {

    }

    /**
     * 为movedView注册onTouch监听，以便用户将手指放到movedView上时，可以移动frame
     * @param addition 额外的触摸监听
     */
    private static void makeMoveable(View touchView, View frame, View.OnTouchListener addition) {
        touchView.setOnTouchListener(new View.OnTouchListener() {
            int[] downPos = new int[2];
            int[] downXY = new int[2];
            WindowManager windowManager = null;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int[] latestPos = new int[]{(int) event.getRawX(), (int) event.getRawY()};
                WindowManager.LayoutParams paramsUpd = (WindowManager.LayoutParams) frame.getLayoutParams();
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downPos = latestPos;
                        downXY = new int[]{paramsUpd.x, paramsUpd.y};
                        windowManager = v.getContext().getSystemService(WindowManager.class);
                        v.setPressed(true);
                        break;

                    case MotionEvent.ACTION_MOVE:
                        paramsUpd.x = downXY[0] + latestPos[0] - downPos[0];
                        paramsUpd.y = downXY[1] + latestPos[1] - downPos[1];
                        if(windowManager != null)
                            windowManager.updateViewLayout(frame, paramsUpd);
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        windowManager = null;
                        v.setPressed(false);

                        if (18 > Math.sqrt(pow(latestPos[0] - downPos[0], 2) + pow(latestPos[1] - downPos[1], 2)))
                            v.performClick();
                        break;
                }
                addition.onTouch(v, event);
                return false;
            }
        });
    }

    /**
     * 添加x11 设置的快捷方式
     */
    private static void createX11SettingsShortcutIfNeeded(Context c) {
        String id = "tx11_preference";
        List<ShortcutInfoCompat> list = ShortcutManagerCompat.getShortcuts(c,
            FLAG_MATCH_MANIFEST | FLAG_MATCH_DYNAMIC | FLAG_MATCH_PINNED | FLAG_MATCH_CACHED);
        for (ShortcutInfoCompat dynamicShortcut : list) {
            if(dynamicShortcut.getId().equals(id))
                return;
        }
        Log.d(TAG, "createX11SettingsShortcutIfNeeded: 开始创建快捷方式");
        ShortcutInfoCompat shortcut = new ShortcutInfoCompat.Builder(c, id)
            .setShortLabel("T:X11 Preferences")
            .setLongLabel("Termux:X11's Preferences")
            .setIcon(IconCompat.createWithResource(c, R.drawable.ic_settings))
            .setIntent(new Intent(c, LoriePreferences.class).setAction(Intent.ACTION_MAIN))
            .build();

        ShortcutManagerCompat.pushDynamicShortcut(c, shortcut);
    }


    static final int ACTIVITY_OTHER = 0;
    static final int ACTIVITY_TAPP = 1;
    static final int ACTIVITY_TX11 = 2;
    @IntDef(value = {ACTIVITY_OTHER, ACTIVITY_TAPP, ACTIVITY_TX11})
    @interface ACTIVE_ACTIVITY {
    }

}
