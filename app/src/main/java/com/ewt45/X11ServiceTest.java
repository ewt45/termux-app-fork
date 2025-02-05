package com.ewt45;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.system.Os;

import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;
import com.termux.x11.CmdEntryPoint;

public class X11ServiceTest extends Service {
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            Os.setenv("TMPDIR", TermuxConstants.TERMUX_TMP_PREFIX_DIR_PATH, true);
            Os.setenv("TERMUX_X11_OVERRIDE_PACKAGE",getPackageName(),true);
            //TODO 需要在另一进程中创建这个，否则会anr
            CmdEntryPoint.main(new String[]{":1"});
//                    new CmdEntryPoint(argsList.toArray(new String[0]));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
