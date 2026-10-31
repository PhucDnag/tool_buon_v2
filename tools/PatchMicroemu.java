import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;

public class PatchMicroemu {
    public static void main(String[] args) throws Exception {
        File origJar = new File("tools/microemulator.jar");
        File tempJar = new File("tools/microemulator.jar.tmp");
        
        JarFile jf = new JarFile(origJar);
        JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempJar));
        
        Enumeration<JarEntry> entries = jf.entries();
        boolean patched = false;
        
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            InputStream is = jf.getInputStream(entry);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            byte[] bytes = baos.toByteArray();
            
            if (entry.getName().equals("org/microemu/app/Main.class")) {
                ClassReader cr = new ClassReader(bytes);
                ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
                cr.accept(new ClassVisitor(Opcodes.ASM5, cw) {
                    public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] ex) {
                        MethodVisitor mv = super.visitMethod(access, name, desc, sig, ex);
                        if (name.equals("main")) {
                            return new MethodVisitor(Opcodes.ASM5, mv) {
                                private boolean pendingZero = false;
                                
                                public void visitInsn(int opcode) {
                                    if (opcode == Opcodes.ICONST_0) {
                                        pendingZero = true;
                                        return;
                                    }
                                    if (pendingZero) {
                                        super.visitInsn(Opcodes.ICONST_0);
                                        pendingZero = false;
                                    }
                                    super.visitInsn(opcode);
                                }
                                
                                public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                                    if (pendingZero) {
                                        if (owner.equals("org/microemu/app/Common") && name.equals("initMIDlet") && desc.equals("(Z)V")) {
                                            System.out.println("Patching initMIDlet(false) -> initMIDlet(true)");
                                            super.visitInsn(Opcodes.ICONST_1); // change false to true!
                                        } else {
                                            super.visitInsn(Opcodes.ICONST_0);
                                        }
                                        pendingZero = false;
                                    }
                                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                                }
                                
                                public void visitVarInsn(int opcode, int var) {
                                    if (pendingZero) {
                                        super.visitInsn(Opcodes.ICONST_0);
                                        pendingZero = false;
                                    }
                                    super.visitVarInsn(opcode, var);
                                }
                                
                                public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                                    if (pendingZero) {
                                        super.visitInsn(Opcodes.ICONST_0);
                                        pendingZero = false;
                                    }
                                    super.visitFieldInsn(opcode, owner, name, desc);
                                }
                            };
                        }
                        return mv;
                    }
                }, 0);
                bytes = cw.toByteArray();
                patched = true;
            }
            
            JarEntry newEntry = new JarEntry(entry.getName());
            newEntry.setTime(entry.getTime());
            jos.putNextEntry(newEntry);
            jos.write(bytes);
            jos.closeEntry();
        }
        
        jf.close();
        jos.close();
        
        if (patched) {
            System.out.println("Main.class was successfully patched!");
            if (!origJar.delete()) {
                throw new IOException("Failed to delete original jar");
            }
            if (!tempJar.renameTo(origJar)) {
                throw new IOException("Failed to rename temp jar to original");
            }
            System.out.println("microemulator.jar successfully updated!");
        } else {
            System.out.println("Main.class not found or not patched!");
        }
    }
}