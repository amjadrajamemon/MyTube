package com.novastream.iptv.core;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Atomic encrypted catalog: Android Keystore AES-256-GCM; no plaintext files or backups. */
public final class SecureStore {
    private final AtomicFile file;
    private static final String ALIAS="nova_catalog_aes_v1";
    public SecureStore(Context context){this.file=new AtomicFile(new File(context.getApplicationContext().getFilesDir(),"catalog.nova"));}
    private SecretKey key() throws Exception {
        KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
        if(ks.containsAlias(ALIAS))return ((KeyStore.SecretKeyEntry)ks.getEntry(ALIAS,null)).getSecretKey();
        KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
        return kg.generateKey();
    }
    public synchronized void save(String json) throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        byte[] encrypted=c.doFinal(json.getBytes(StandardCharsets.UTF_8));
        byte[] iv=c.getIV();byte[] packed=new byte[iv.length+encrypted.length];
        System.arraycopy(iv,0,packed,0,iv.length);System.arraycopy(encrypted,0,packed,iv.length,encrypted.length);
        FileOutputStream stream=null;
        try {stream=file.startWrite();stream.write(packed);file.finishWrite(stream);}
        catch(Exception e){if(stream!=null)file.failWrite(stream);throw e;}
    }
    public synchronized String load() throws Exception {
        byte[] b;
        try(InputStream in=file.openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){out.write(buffer,0,n);if(out.size()>24*1024*1024)throw new IOException("Encrypted catalog exceeds size limit");}
            b=out.toByteArray();
        } catch(FileNotFoundException missing){return "";}
        if(b.length<28)throw new IOException("Encrypted library data is incomplete");
        byte[] iv=new byte[12];System.arraycopy(b,0,iv,0,12);
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
        return new String(c.doFinal(b,12,b.length-12),StandardCharsets.UTF_8);
    }
    public synchronized void reset(){file.delete();}
}
