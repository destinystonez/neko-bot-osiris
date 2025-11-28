package org.nekotori.util;

import org.apache.commons.codec.binary.Base64;

import java.io.FileOutputStream;
import java.io.IOException;

public class Base64ToImageWithCommons {
    
    public static boolean base64ToImage(String base64Str, String outputPath) {
        try {
            // 清理Base64字符串
            String base64Image = base64Str.contains(",") ? 
                base64Str.split(",")[1] : base64Str;
            
            // 解码
            byte[] imageBytes = Base64.decodeBase64(base64Image);
            
            // 写入文件
            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                fos.write(imageBytes);
            }
            
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}