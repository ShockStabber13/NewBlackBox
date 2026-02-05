package top.niunaijun.blackbox.core.system.pm.installer;


import java.io.File;
import java.io.IOException;

import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.core.system.pm.BPackageSettings;
import top.niunaijun.blackbox.entity.pm.InstallOption;
import top.niunaijun.blackbox.utils.FileUtils;
import top.niunaijun.blackbox.utils.NativeUtils;

/**
 * updated by alex5402 on 4/24/21.
 * * ∧＿∧
 * (`･ω･∥
 * 丶　つ０
 * しーＪ
 * TFNQw5HgWUS33Ke1eNmSFTwoQySGU7XNsK (USDT TRC20)
 * 拷贝文件相关
 */
public class CopyExecutor implements Executor {

    @Override
    public int exec(BPackageSettings ps, InstallOption option, int userId) {
        try {
            if (!option.isFlag(InstallOption.FLAG_SYSTEM)) {
                NativeUtils.copyNativeLib(new File(ps.pkg.baseCodePath), BEnvironment.getAppLibDir(ps.pkg.packageName));
            }
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
        if (option.isFlag(InstallOption.FLAG_STORAGE)) {
            // 外部安装
            File origFile = new File(ps.pkg.baseCodePath);
            File newFile = BEnvironment.getBaseApkDir(ps.pkg.packageName);
            try {
                if (option.isFlag(InstallOption.FLAG_URI_FILE)) {
                    boolean b = FileUtils.renameTo(origFile, newFile);
                    if (!b) {
                        FileUtils.copyFile(origFile, newFile);
                    }
                } else {
                    FileUtils.copyFile(origFile, newFile);
                }
                newFile.setReadOnly();
                // update baseCodePath
                ps.pkg.baseCodePath = newFile.getAbsolutePath();

                // Split APKs: copy each split into the same virtual app dir and update paths.
                if (ps.pkg.splitNames != null && ps.pkg.splitCodePaths != null &&
                        ps.pkg.splitNames.length == ps.pkg.splitCodePaths.length) {
                    String[] newSplitPaths = new String[ps.pkg.splitCodePaths.length];
                    for (int i = 0; i < ps.pkg.splitCodePaths.length; i++) {
                        String splitName = ps.pkg.splitNames[i];
                        String splitPath = ps.pkg.splitCodePaths[i];
                        if (splitName == null || splitName.length() == 0) {
                            // fallback: derive a stable name
                            splitName = "split" + i;
                        }
                        File splitOrig = new File(splitPath);
                        File splitNew = BEnvironment.getSplitApkDir(ps.pkg.packageName, splitName);
                        splitNew.getParentFile().mkdirs();
                        FileUtils.copyFile(splitOrig, splitNew);
                        splitNew.setReadOnly();
                        newSplitPaths[i] = splitNew.getAbsolutePath();
                    }
                    ps.pkg.splitCodePaths = newSplitPaths;
                }
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
        } else if (option.isFlag(InstallOption.FLAG_SYSTEM)) {
            // 系统安装
        }
        return 0;
    }
}
