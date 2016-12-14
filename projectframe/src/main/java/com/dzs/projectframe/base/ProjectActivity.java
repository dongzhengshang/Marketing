package com.dzs.projectframe.base;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.view.WindowManager;

import com.dzs.projectframe.Conif;
import com.dzs.projectframe.R;
import com.dzs.projectframe.adapter.ViewHolder;
import com.dzs.projectframe.utils.AsyncTaskUtils;
import com.dzs.projectframe.utils.DiskLruCacheHelpUtils;
import com.dzs.projectframe.utils.FileUtils;
import com.dzs.projectframe.utils.LogUtils;
import com.dzs.projectframe.utils.SharedPreferUtils;
import com.dzs.projectframe.utils.StackUtils;
import com.dzs.projectframe.utils.ToastUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author DZS dzsdevelop@163.com
 * @version V1.0
 * @date 2016/8/19.
 */
public abstract class ProjectActivity extends FragmentActivity implements View.OnClickListener, AsyncTaskUtils.OnNetReturnListener {
    protected ViewHolder viewUtils;
    protected Resources resources;
    protected SharedPreferUtils sharedPreferUtils;

    protected abstract int setContent();

    protected abstract void initView();

    protected abstract void initAnimation();

    protected abstract void initData();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        StackUtils.getInstanse().addActivity(this);
        setTextAbout();
        viewUtils = ViewHolder.get(this, setContent());
        setContentView(viewUtils.getView());
        resources = ProjectContext.resources;
        sharedPreferUtils = ProjectContext.sharedPreferUtils;
        initView();
        initAnimation();
        initData();
    }

    @Override
    protected void onPause() {
        super.onPause();
        DiskLruCacheHelpUtils.getInstanse().flush();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        StackUtils.getInstanse().finishActivity(this);
    }

    /**
     * 设置字体相关
     */
    private void setTextAbout() {
        Resources res = getResources();
        Configuration config = new Configuration();
        config.setToDefaults();
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

    //跳转到某一Activity
    protected void Intent(Class activity) {
        Intent(activity, null, false);
    }

    /**
     * 跳转到某一Activity
     *
     * @param activity activity
     * @param isFinish 是否销毁当前界面
     */
    protected void Intent(Class activity, boolean isFinish) {
        Intent(activity, null, isFinish);
    }

    /**
     * 跳转到某一Activity
     *
     * @param activity activity
     * @param data     数据Bean,需要实现Serializable接口
     * @param isFinish 是否销毁当前界面
     */
    protected void Intent(Class activity, Class data, boolean isFinish) {
        Intent intent = new Intent(this, activity);
        if (data != null) intent.putExtra(activity.getName(), data);
        startActivity(intent);
        if (isFinish) finish();
    }

    //设置全屏
    protected void setFullScream() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    //--------------------------权限管理-------------------------------------------
    //判断是否授权
    protected boolean isPermissionGranted(String permissionName, int questCode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        //判断是否需要请求允许权限
        if (checkSelfPermission(permissionName) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permissionName}, questCode);
            return false;
        }
        return true;
    }

    //判断是否授权,批量处理
    protected boolean isPermissionsAllGranted(String[] permArray, int questCode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        //获得批量请求但被禁止的权限列表
        List<String> deniedPerms = new ArrayList<>();
        for (int i = 0; permArray != null && i < permArray.length; i++) {
            if (PackageManager.PERMISSION_GRANTED != checkSelfPermission(permArray[i])) {
                deniedPerms.add(permArray[i]);
            }
        }
        //进行批量请求
        int denyPermNum = deniedPerms.size();
        if (denyPermNum != 0) {
            requestPermissions(deniedPerms.toArray(new String[denyPermNum]), questCode);
            return false;
        }
        return true;
    }

    //------------------相机操作相关------------------------
    protected static final int SYS_INTENT_REQUEST = 0XFF01;//系统相册
    protected static final int CAMERA_INTENT_REQUEST = 0XFF02;//调用系统相机
    protected static final int IMAGE_CUT = 0XFF03;//裁剪
    protected File tempPhotoImageFile = FileUtils.getSaveFile("TempImage", "tempPhoto.jpeg");
    protected Uri photoUri = tempPhotoImageFile == null ? null : Uri.fromFile(tempPhotoImageFile);
    protected String photoPath = tempPhotoImageFile == null ? "" : tempPhotoImageFile.getAbsolutePath();
    protected File tempCropImageFile = FileUtils.getSaveFile("TempImage", "tempCrop.jpeg");
    protected Uri cropUri = tempCropImageFile == null ? null : Uri.fromFile(tempCropImageFile);
    protected String cropPath = tempCropImageFile == null ? "" : tempCropImageFile.getAbsolutePath();

    //调用系统相册
    protected void systemPhoto() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_PICK);
        startActivityForResult(intent, SYS_INTENT_REQUEST);
    }

    //调用系统相机
    protected void cameraPhoto(Conif.OperationResult result) {
        try {
            if (!FileUtils.checkSDcard()) {
                result.onResult(Conif.OperationResultType.FAIL.setMessage(ProjectContext.appContext.getString(R.string.SDError)));
                return;
            }
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            startActivityForResult(intent, CAMERA_INTENT_REQUEST);
        } catch (Exception e) {
            result.onResult(Conif.OperationResultType.FAIL.setMessage(ProjectContext.appContext.getString(R.string.OpenCameraError)));
            LogUtils.exception(e);
        }
    }

    //图片剪切
    protected void cropImageUri(Uri uri, int aspectX, int aspectY, int outputX, int outputY, int requestCode) {
        Intent intent = new Intent("com.android.camera.action.CROP");
        intent.setDataAndType(uri, "image/*");
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", aspectX);
        intent.putExtra("aspectY", aspectY);
        intent.putExtra("outputX", outputX);
        intent.putExtra("outputY", outputY);
        intent.putExtra("scale", true);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cropUri);
        intent.putExtra("return-data", false);
        intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString());
        intent.putExtra("noFaceDetection", true);
        startActivityForResult(intent, requestCode);
    }
}
