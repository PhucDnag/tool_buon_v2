import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;

/** Fail-closed patcher: required integration points must all exist in the input JAR. */
public final class PatchClient implements Opcodes {
    private static final Set<String> hits=new HashSet<String>();
    private static final String MOD="autotrade/ClientMod";
    private static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] bytes=new byte[8192]; int n;
        while ((n=in.read(bytes))!=-1) out.write(bytes,0,n); in.close(); return out.toByteArray();
    }
    private static byte[] patch(final String owner,byte[] data) {
        ClassReader reader=new ClassReader(data); ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(ASM5,writer) {
            public MethodVisitor visitMethod(int access,final String name,final String desc,String signature,String[] exceptions) {
                MethodVisitor target=super.visitMethod(access,name,desc,signature,exceptions);
                return new MethodVisitor(ASM5,target) {
                    private void call(String name,String descriptor) { super.visitMethodInsn(INVOKESTATIC,MOD,name,descriptor,false); hits.add(name); }
                    private final Set<Label> networkHandlers=new HashSet<Label>();
                    public void visitTryCatchBlock(Label start,Label end,Label handler,String type) {
                        super.visitTryCatchBlock(start,end,handler,type);
                        if (owner.equals("CLib/Session_ME$MessageCollector") && name.equals("run") && type!=null)
                            networkHandlers.add(handler);
                    }
                    public void visitLabel(Label label) {
                        super.visitLabel(label);
                        if (networkHandlers.contains(label)) {
                            super.visitInsn(DUP);
                            super.visitMethodInsn(INVOKESTATIC,"autotrade/ConnectionTrace","failure","(Ljava/lang/Throwable;)V",false);
                            hits.add("traceNetworkError");
                        }
                    }
                    public void visitInsn(int opcode) {
                        if (opcode==ARETURN && owner.equals("CLib/Session_ME$MessageCollector") && name.equals("readMessage")) {
                            super.visitInsn(DUP);
                            Label absent=new Label(); super.visitJumpInsn(IFNULL,absent);
                            super.visitMethodInsn(INVOKESTATIC,"autotrade/RuntimeMaintenance","received","()V",false);
                            super.visitLabel(absent); hits.add("healthReceive");
                        }
                        if (opcode==RETURN) {
                            if (owner.equals("netcommand/global/ReadMessenge") && name.equals("npcInfo")) call("finishTradeShop","()V");
                            if (owner.equals("Main/GameCanvas") && name.equals("update") && desc.equals("()V")) call("tick","()V");
                            if (owner.equals("GameScreen/GameScreen") && name.equals("doMenu") && desc.equals("()V")) call("appendMenu","()V");
                            if (owner.equals("GameScreen/GameScreen") && name.equals("paint") && desc.equals("(LCLib/mGraphics;)V")) {
                                super.visitVarInsn(ALOAD,1); call("paint","(LCLib/mGraphics;)V");
                            }
                            if (owner.equals("netcommand/global/GlobalLogicHandler") && name.equals("onDisconnect") && desc.equals("()V")) call("disconnected","()V");
                            if (owner.equals("Model/Menu2") && name.equals("setinfoDynamic") && desc.equals("(LCLib/mVector;IIILjava/lang/String;)V")) {
                                super.visitVarInsn(ALOAD,1); super.visitVarInsn(ILOAD,3); super.visitVarInsn(ILOAD,4); super.visitVarInsn(ALOAD,5);
                                call("serverMenu","(LCLib/mVector;IILjava/lang/String;)V");
                            }
                            if (owner.equals("InterfaceComponents/MsgDialog") && name.equals("setinfo")) {
                                if (desc.equals("(Ljava/lang/String;LCLib/mVector;)V")) {
                                    super.visitVarInsn(ALOAD,0); super.visitVarInsn(ALOAD,1); super.visitVarInsn(ALOAD,2);
                                    call("serverDialog","(Ljava/lang/Object;Ljava/lang/String;LCLib/mVector;)V");
                                } else if (desc.equals("(Ljava/lang/String;LInterfaceComponents/iCommand;Z)V")) {
                                    super.visitVarInsn(ALOAD,0); super.visitVarInsn(ALOAD,1); super.visitVarInsn(ALOAD,2);
                                    call("serverDialogOne","(Ljava/lang/Object;Ljava/lang/String;LInterfaceComponents/iCommand;)V");
                                }
                            }
                            if (owner.equals("InterfaceComponents/TabShopNew") && name.equals("<init>") && desc.equals("(LCLib/mVector;BLjava/lang/String;IB)V")) {
                                super.visitVarInsn(ALOAD,1); super.visitVarInsn(ILOAD,4); super.visitVarInsn(ILOAD,5);
                                super.visitVarInsn(ALOAD,3);
                                call("serverNamedShop","(LCLib/mVector;IILjava/lang/String;)V");
                            }
                        }
                        super.visitInsn(opcode);
                    }
                    public void visitCode() {
                        if (owner.equals("GameScreen/GameScreen") && name.equals("paint") && desc.equals("(LCLib/mGraphics;)V")) {
                            super.visitVarInsn(ALOAD,1); call("paintLowDetail","(LCLib/mGraphics;)Z");
                            Label normal=new Label(); super.visitJumpInsn(IFEQ,normal); super.visitInsn(RETURN); super.visitLabel(normal);
                        }
                        if (owner.equals("GameObjects/MainObject") && (name.equals("paintDataEff_Top") || name.equals("paintDataEff_Bot")
                                || name.equals("paintEffauto") || name.equals("paintEffauto_Low") || name.equals("paintBuffFirst")
                                || name.equals("paintBuffLast") || name.equals("paintEffectCharWearing"))) {
                            call("hideEffects","()Z"); Label normal=new Label(); super.visitJumpInsn(IFEQ,normal); super.visitInsn(RETURN); super.visitLabel(normal);
                        }
                        if (owner.equals("Main/GameCanvas") && name.equals("paint") && desc.equals("(LCLib/TemGraphics;)V")) {
                            call("shouldPaint","()Z");
                            Label render=new Label(); super.visitJumpInsn(IFNE,render); super.visitInsn(RETURN); super.visitLabel(render);
                        }
                        if (owner.equals("GameScreen/LoginScreen") && name.equals("Show") && desc.equals("()V"))
                            call("prepareManagerLogin","()V");
                        if (owner.equals("GameScreen/LoginScreen") && (name.equals("setScreen") || name.equals("loadUser_Pass")) && desc.equals("()V")) {
                            call("applyManagerLogin","()Z");
                            if (name.equals("loadUser_Pass")) {
                                Label legacy=new Label(); super.visitJumpInsn(IFEQ,legacy);
                                super.visitInsn(RETURN); super.visitLabel(legacy);
                            } else super.visitInsn(POP);
                            hits.add("managerLogin/"+name);
                        }
                        super.visitCode();
                        if (owner.equals("CLib/Session_ME") && name.equals("doSendMessage")) {
                            super.visitMethodInsn(INVOKESTATIC,"autotrade/RuntimeMaintenance","sent","()V",false);
                            hits.add("healthSend");
                            super.visitVarInsn(ALOAD,1);
                            super.visitFieldInsn(GETFIELD,"net/Message","command","B");
                            super.visitMethodInsn(INVOKESTATIC,"autotrade/ConnectionTrace","packet","(I)V",false);
                            hits.add("traceSend");
                        }
                        if (owner.equals("CLib/Session_ME") && name.equals("connect") && desc.equals("(Ljava/lang/String;I)V")) {
                            super.visitLdcInsn("CONNECT");
                            super.visitMethodInsn(INVOKESTATIC,"autotrade/ConnectionTrace","event","(Ljava/lang/String;)V",false);
                            hits.add("traceConnect");
                        }
                        if (owner.equals("InterfaceComponents/MsgDialog") && name.equals("paint") && desc.equals("(LCLib/mGraphics;)V")) {
                            super.visitVarInsn(ALOAD,0); call("hideTradeDialog","(Ljava/lang/Object;)Z");
                            Label visible=new Label(); super.visitJumpInsn(IFEQ,visible); super.visitInsn(RETURN); super.visitLabel(visible);
                        }
                        if (owner.equals("GameScreen/TabScreenNew") && name.equals("Show") && desc.equals("(LGameScreen/MainScreen;)V")) {
                            call("hideTradeShop","()Z");
                            Label visible=new Label(); super.visitJumpInsn(IFEQ,visible); super.visitInsn(RETURN); super.visitLabel(visible);
                        }
                        if (owner.equals("GameScreen/TabScreenNew") && name.equals("paint") && desc.equals("(LCLib/mGraphics;)V")) {
                            call("hideTradeShop","()Z");
                            Label visible=new Label(); super.visitJumpInsn(IFEQ,visible);
                            super.visitVarInsn(ALOAD,1); call("paintTradeFallback","(LCLib/mGraphics;)V");
                            super.visitInsn(RETURN); super.visitLabel(visible);
                        }
                        if (owner.equals("Model/Menu2") && name.equals("paintMenu") && desc.equals("(LCLib/mGraphics;)V")) {
                            call("hideTradeMenu","()Z");
                            Label visible=new Label(); super.visitJumpInsn(IFEQ,visible); super.visitInsn(RETURN); super.visitLabel(visible);
                        }
                        if (owner.equals("netcommand/global/GlobalService") && name.equals("chat_npc") && desc.equals("(B)V")) {
                            super.visitVarInsn(ILOAD,1); call("stockRequested","(I)V");
                        }
                        if (owner.equals("Main/GameCanvas") && name.equals("pointerReleased") && desc.equals("(II)V")) {
                            super.visitVarInsn(ILOAD,1); super.visitVarInsn(ILOAD,2); call("pointerClick","(II)V");
                        }
                        if (owner.equals("Main/GameCanvas") && name.equals("keyPressed") && desc.equals("(I)V")) {
                            super.visitVarInsn(ILOAD,1); call("keyPress","(I)V");
                        }
                    }
                    public void visitMethodInsn(int opcode,String calledOwner,String calledName,String descriptor,boolean isInterface) {
                        if (owner.equals("netcommand/global/ReadMessenge") && name.equals("npcInfo")
                                && calledOwner.equals("Main/GameCanvas") && calledName.equals("end_Dialog") && descriptor.equals("()V")) {
                            call("finishNpcDialog","()V"); return;
                        }
                        boolean info=owner.equals("netcommand/global/ReadMessenge") && name.equals("InfoEasyFromServer")
                                && calledOwner.equals("Main/GameCanvas") && (calledName.equals("addInfoChar") || calledName.equals("addInfoCharServer"))
                                && descriptor.equals("(Ljava/lang/String;)V");
                        if (info) super.visitInsn(DUP);
                        super.visitMethodInsn(opcode,calledOwner,calledName,descriptor,isInterface);
                        if (info) call("serverText","(Ljava/lang/String;)V");
                    }
                    public void visitFieldInsn(int opcode,String fieldOwner,String field,String descriptor) {
                        if (owner.equals("GameObjects/MainObject") && (name.equals("paintName") || name.equals("paintNameFocus"))
                                && opcode==GETFIELD && field.equals("name") && descriptor.equals("Ljava/lang/String;")) {
                            call("displayName","(LGameObjects/MainObject;)Ljava/lang/String;"); return;
                        }
                        boolean npc=owner.equals("netcommand/global/ReadMessenge") && (name.equals("chat_npc") || name.equals("npcInfo") || name.equals("chatPopup")) && opcode==PUTFIELD && field.equals("strChatPopup");
                        if (npc) super.visitInsn(DUP2);
                        super.visitFieldInsn(opcode,fieldOwner,field,descriptor);
                        if (npc) { super.visitInsn(POP); call("npcChat","(LGameObjects/MainObject;)V"); hits.add("npcChat/"+name); }
                    }
                };
            }
        },0);
        return writer.toByteArray();
    }
    private static void addFiles(JarOutputStream out,File root,File directory) throws IOException {
        File[] files=directory.listFiles(); Arrays.sort(files);
        for (File file:files) {
            if (file.isDirectory()) addFiles(out,root,file);
            else if (file.getName().endsWith(".class")) {
                String name=root.toURI().relativize(file.toURI()).getPath();
                JarEntry entry=new JarEntry(name); entry.setTime(0); out.putNextEntry(entry); out.write(read(new FileInputStream(file))); out.closeEntry();
            }
        }
    }
    public static void main(String[] args) throws Exception {
        JarFile input=new JarFile(args[0]); File destination=new File(args[2]);
        File temporary=new File(args[2]+".tmp");
        JarOutputStream out=new JarOutputStream(new FileOutputStream(temporary));
        try {
            Enumeration<JarEntry> entries=input.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry=entries.nextElement(); String name=entry.getName();
                if (name.equals("CLib/mSocket.class")) continue;
                if (name.endsWith(".SF") || name.endsWith(".RSA") || name.endsWith(".DSA")) continue;
                byte[] bytes=read(input.getInputStream(entry));
                if (name.endsWith(".class")) {
                    String owner=name.substring(0,name.length()-6);
                    if (owner.equals("GameScreen/LoginScreen") || owner.equals("CLib/Session_ME") || owner.equals("CLib/Session_ME$MessageCollector") || owner.equals("Main/GameCanvas") || owner.equals("GameScreen/GameScreen") || owner.equals("GameObjects/MainObject")
                            || owner.equals("netcommand/global/GlobalLogicHandler") || owner.equals("netcommand/global/GlobalService")
                            || owner.equals("netcommand/global/ReadMessenge") || owner.equals("Model/Menu2")
                            || owner.equals("InterfaceComponents/MsgDialog") || owner.equals("InterfaceComponents/TabShopNew")
                            || owner.equals("GameScreen/TabScreenNew")) bytes=patch(owner,bytes);
                }
                JarEntry copy=new JarEntry(name); copy.setTime(0); out.putNextEntry(copy); out.write(bytes); out.closeEntry();
            }
            addFiles(out,new File(args[1]),new File(args[1]));
        } finally { out.close(); input.close(); }
        String[] required={"healthReceive","healthSend","traceSend","traceConnect","traceNetworkError","tick","appendMenu","paint","disconnected","serverMenu","displayName","npcChat","npcChat/chat_npc","npcChat/npcInfo","npcChat/chatPopup","stockRequested","serverDialog","serverDialogOne","serverNamedShop","serverText","pointerClick","keyPress","hideTradeShop","hideTradeMenu"};
        for (String hook:required) if (!hits.contains(hook)) throw new IllegalStateException("Missing hook: "+hook);
        for (String hook:new String[]{"managerLogin/setScreen","managerLogin/loadUser_Pass","prepareManagerLogin","shouldPaint","paintLowDetail","hideEffects"})
            if (!hits.contains(hook)) throw new IllegalStateException("Missing hook: "+hook);
        java.nio.file.Files.move(temporary.toPath(),destination.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Patched hooks: "+hits);
    }
}
