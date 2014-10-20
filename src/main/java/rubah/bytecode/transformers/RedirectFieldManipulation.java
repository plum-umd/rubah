/*******************************************************************************
 *  	Copyright 2014,
 *  		Luis Pina <luis@luispina.me>,
 *  		Michael Hicks <mwh@cs.umd.edu>
 *  	
 *  	This file is part of Rubah.
 *
 *     Rubah is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Rubah is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Rubah.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package rubah.bytecode.transformers;

import java.io.PrintWriter;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ListIterator;
import java.util.Map.Entry;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import rubah.Rubah;
import rubah.bytecode.RubahProxy;
import rubah.framework.Clazz;
import rubah.framework.Method;
import rubah.framework.Namespace;
import rubah.framework.Type;
import rubah.runtime.Version;

public class RedirectFieldManipulation extends RubahTransformer {
	private static final HashSet<MethodInvocationInfo> ensureNotProxyMethods =
			new HashSet<MethodInvocationInfo>(Arrays.asList(new MethodInvocationInfo[]{
				new MethodInvocationInfo("getClass", Object.class, Class.class),
			}));
	private Version version;

	public RedirectFieldManipulation(HashMap<String, Object> objectsMap,
			Version version, ClassVisitor visitor) {
		super(objectsMap, version.getNamespace(), visitor);
		this.version = version;
	}

	public RedirectFieldManipulation(HashMap<String, Object> objectsMap,
			Namespace namespace, ClassVisitor visitor) {
		super(objectsMap, namespace, visitor);
	}

	public RedirectFieldManipulation(Namespace namespace, ClassVisitor visitor) {
		super(null, namespace, visitor);
	}

	@Override
	public MethodVisitor visitMethod(int access, String name,
			final String desc, String signature, String[] exceptions) {

		final MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);

		if (this.namespace.isBootstrap(this.thisClass))
			return methodVisitor;

		if (this.thisClass.getFqn().startsWith("java.io")
				|| this.thisClass.getFqn().startsWith("sun.reflect")
				|| this.thisClass.getFqn().startsWith("sun.misc")
				|| this.thisClass.getFqn().startsWith("java.security")
				|| this.thisClass.getFqn().startsWith("java.util.concurrent.locks")
				|| this.thisClass.getFqn().startsWith("java.util.concurrent.atomic")
				|| (this.thisClass.getFqn().startsWith("java.lang") && !this.thisClass.getFqn().equals(Class.class.getName())))
			return methodVisitor;

		if (this.objectsMap != null) {
			Method m = (Method) objectsMap.get(name);

			if (m == null)
				return methodVisitor;

			if (m.getName().startsWith(AddGettersAndSetters.GETTER_PREFFIX) || m.getName().startsWith(AddGettersAndSetters.SETTER_PREFFIX))
				return methodVisitor;
		}

		MethodVisitor ret =  new MethodNode(ASM5, access, name, desc, signature, exceptions) {
			private Frame<SourceValue>[] sourcesFrames;
			private boolean isStatic = Modifier.isStatic(access);

			@Override
			public void visitEnd() {
				Analyzer<SourceValue> sourceAnalyzer = new Analyzer<SourceValue>(
						new SourceInterpreter());

				try {
					sourceAnalyzer.analyze(thisClass.getASMType().getInternalName(), this);
				} catch (AnalyzerException e) {
					System.out.println(namespace.isBootstrap(thisClass));
					System.out.println(e.getMessage());
					this.sourcesFrames = sourceAnalyzer.getFrames();
					this.printAnalyzerResult();
					throw new Error(e);
				}

				this.sourcesFrames = sourceAnalyzer.getFrames();

				ListIterator<AbstractInsnNode> iter = this.instructions.iterator();
				HashMap<AbstractInsnNode, InsnList> instructionsToAddBefore = new HashMap<AbstractInsnNode, InsnList>();
				HashMap<AbstractInsnNode, InsnList> instructionsToAddAfter = new HashMap<AbstractInsnNode, InsnList>();
				HashMap<AbstractInsnNode, AbstractInsnNode> instructionsToReplace = new HashMap<AbstractInsnNode, AbstractInsnNode>();

				while (iter.hasNext()) {
					AbstractInsnNode insnNode = iter.next();

					int opcode;
					switch ((opcode = insnNode.getOpcode())) {
						case INVOKESPECIAL:
						{
							MethodInsnNode methodNode = (MethodInsnNode) insnNode;

							int receiverDepth = Type.getArgumentTypes(methodNode.desc).length;

							if (!this.needsRedirect(insnNode, receiverDepth))
								continue;

							for (AbstractInsnNode source : this.getSources(insnNode, receiverDepth)) {
								if (source.getOpcode() == AALOAD)
									// Already instrumented, skip it
									continue;
								instructionsToAddAfter.put(source, this.ensureNotProxy(methodNode.owner));
							}

							break;
						}
						case INVOKEVIRTUAL:
						{
							MethodInsnNode methodNode = (MethodInsnNode) insnNode;
							MethodInvocationInfo m = new MethodInvocationInfo(methodNode.name, methodNode.owner, methodNode.desc);

							if (ensureNotProxyMethods.contains(m)) {
								int receiverDepth = 0;
								for (Type arg : Type.getArgumentTypes(methodNode.desc))
									receiverDepth += arg.getSize();

								if (!this.needsRedirect(insnNode, receiverDepth))
									continue;

								for (AbstractInsnNode source : this.getSources(insnNode, receiverDepth))
									instructionsToAddAfter.put(source, this.ensureNotProxy());
							}

							break;
						}
						case GETFIELD:
						{
							if (!this.needsRedirect(insnNode, 0))
								continue;
							FieldInsnNode fieldNode = (FieldInsnNode) insnNode;
							Type fieldOwner = findActualFieldOwner(Type.getObjectType(fieldNode.owner), fieldNode.name);
							opcode = (opcode == GETFIELD ? INVOKEVIRTUAL : INVOKESTATIC);
							String methodName = AddGettersAndSetters.generateGetterName(version, fieldOwner, fieldNode.name);
							String methodDesc = Type.getMethodDescriptor(Type.getType(fieldNode.desc));
							instructionsToReplace.put(insnNode, new MethodInsnNode(opcode, fieldNode.owner, methodName, methodDesc, false));
							break;
						}
						case PUTFIELD:
						{
							if (!this.needsRedirect(insnNode, 1))
								continue;
							FieldInsnNode fieldNode = (FieldInsnNode) insnNode;
							Type fieldOwner = findActualFieldOwner(Type.getObjectType(fieldNode.owner), fieldNode.name);
							opcode = (opcode == PUTFIELD ? INVOKEVIRTUAL : INVOKESTATIC);
							String methodName = AddGettersAndSetters.generateSetterName(version, fieldOwner, fieldNode.name);
							String methodDesc = Type.getMethodDescriptor(Type.VOID_TYPE, Type.getType(fieldNode.desc));
							instructionsToReplace.put(insnNode, new MethodInsnNode(opcode, fieldNode.owner, methodName, methodDesc, false));
							break;
						}
					}
				}

				for (Entry<AbstractInsnNode, InsnList> entry : instructionsToAddBefore.entrySet())
					this.instructions.insertBefore(entry.getKey(), entry.getValue());

				for (Entry<AbstractInsnNode, InsnList> entry : instructionsToAddAfter.entrySet())
					this.instructions.insert(entry.getKey(), entry.getValue());

				// Destructive changes take place after constructive changes
				// so that the location nodes do not get destroyed too soon
				for (Entry<AbstractInsnNode, AbstractInsnNode> entry : instructionsToReplace.entrySet())
					this.instructions.set(entry.getKey(), entry.getValue());

				accept(methodVisitor);
			}

			private InsnList ensureNotProxy() {
				return this.ensureNotProxy(null);
			}

			private InsnList ensureNotProxy(String owner) {
				InsnList list = new InsnList();
				LabelNode label = new LabelNode();
				list.add(new InsnNode(DUP));
				list.add(new TypeInsnNode(INSTANCEOF, Type.getType(RubahProxy.class).getInternalName()));
				list.add(new JumpInsnNode(IFEQ, label));
				list.add(new TypeInsnNode(CHECKCAST, Type.getType(RubahProxy.class).getInternalName()));
				list.add(new MethodInsnNode(
						INVOKESTATIC,
						Type.getType(Rubah.class).getInternalName(),
						"getConverted",
						Type.getMethodDescriptor(Type.getType(Object.class), Type.getType(Object.class)),
						false));
				if (owner != null)
					list.add(new TypeInsnNode(CHECKCAST, owner));
				list.add(label);

				return list;
			}

			/**
			 *
			 * @param idx
			 * @return True if local var, false if argument
			 */
			private boolean isLocalVar(int idx) {
				int lastVar = (this.isStatic ? 0 : 1);
				for (Type arg : Type.getArgumentTypes(desc))
					lastVar += arg.getSize();

				return idx >= lastVar;

			}

			private Set<AbstractInsnNode> getSources(AbstractInsnNode insnNode, int depth) {
				return this.getSources(insnNode, depth, new HashSet<AbstractInsnNode>(), new HashSet<AbstractInsnNode>());
			}

			private Set<AbstractInsnNode> getSources(AbstractInsnNode insnNode, int depth, HashSet<AbstractInsnNode> allSources, HashSet<AbstractInsnNode> alreadySeen) {
				int idx = this.instructions.indexOf(insnNode);
				Frame<SourceValue> sourcesFrame = this.sourcesFrames[idx];
				if (sourcesFrame == null) {
					// Bug in the analyzer or unreachable code
					return new HashSet<AbstractInsnNode>();
				}
				Set<AbstractInsnNode> sources = sourcesFrame.getStack(sourcesFrame.getStackSize() - 1
						- depth).insns;

				for (AbstractInsnNode source : sources) {
					if (alreadySeen.contains(source))
						continue;

					alreadySeen.add(source);

					switch (source.getOpcode()) {
						case CHECKCAST:
						case DUP:
							allSources.addAll(this.getSources(source, 0, allSources, alreadySeen));
							break;
						case ALOAD:
							// Is this an argument?
							VarInsnNode n = (VarInsnNode) source;
							if (isLocalVar(n.var)) {
								// Only ASTORE can save to local variables
								for (AbstractInsnNode astore : sourcesFrame.getLocal(n.var).insns) {
									allSources.addAll(this.getSources(astore, 0, allSources, alreadySeen));
								}
								continue;
							}
							// Explicit fall-through
						default:
							allSources.add(source);
							break;
					}
				}

				return allSources;
			}

			private void printAnalyzerResult() {
			        Textifier t = new Textifier();
			        TraceMethodVisitor mv = new TraceMethodVisitor(t);
		            PrintWriter pw = new PrintWriter(System.out);

			        pw.println(this.name + this.desc);
			        for (int j = 0; j < this.instructions.size(); ++j) {
			            this.instructions.get(j).accept(mv);

			            StringBuffer s = new StringBuffer();
			            Frame<SourceValue> f = this.sourcesFrames[j];
			            if (f == null) {
			                s.append('?');
			            } else {
			                for (int k = 0; k < f.getLocals(); ++k) {
			                	for (AbstractInsnNode insn : f.getLocal(k).insns) {
				                    s.append(this.instructions.indexOf(insn))
				                            .append(' ');
			                	}
			                }
			                s.append(" : ");
			                for (int k = 0; k < f.getStackSize(); ++k) {
			                	for (AbstractInsnNode insn : f.getStack(k).insns) {
				                    s.append(this.instructions.indexOf(insn))
				                            .append(' ');
			                	}
			                }
			            }
			            while (s.length() < this.maxStack + this.maxLocals + 1) {
			                s.append(' ');
			            }
			            pw.print(Integer.toString(j + 100000).substring(1));
			            pw.print(" " + s + " : " + t.text.get(t.text.size() - 1));
			        }
			        for (int j = 0; j < this.tryCatchBlocks.size(); ++j) {
			            this.tryCatchBlocks.get(j).accept(mv);
			            pw.print(" " + t.text.get(t.text.size() - 1));
			        }
			        pw.println();
			        pw.flush();
			    }

			private boolean needsRedirect(AbstractInsnNode insnNode, int stackDepth) {
				boolean ret = false;

				for (AbstractInsnNode insn : this.getSources(insnNode, stackDepth)) {
					switch (insn.getOpcode()) {
					case NEW:
						continue;
					case ALOAD:
						if (((VarInsnNode)insn).var != 0 || this.isStatic)
							ret = true;
						break;
					default:
						ret = true;
						break;
					}
				}

				return ret;
			}

		};

		return ret;
	}

	private Type findActualFieldOwner(Type start, String fieldName) {
		Clazz c = this.namespace.getClass(start);

		while (c != null) {
			for (rubah.framework.Field f : c.getFields()) {
				if (f.getName().equals(fieldName))
					return c.getASMType();
			}

			c = c.getParent();
		}

		return start;
	}

	private static final class MethodInvocationInfo {
		private final String name;
		private final String owner;
		private final String desc;

		public MethodInvocationInfo(String name, Class<?> owner, Class<?> ret, Class<?> ... args) {
			this.name = name;
			this.owner = Type.getType(owner).getInternalName();
			Type[] argsType = new Type[args.length];
			for (int i = 0; i < args.length; i++)
				argsType[i] = Type.getType(args[i]);
			this.desc = Type.getMethodDescriptor(Type.getType(ret), argsType);
		}

		public MethodInvocationInfo(String name, String owner, String desc) {
			this.name = name;
			this.owner = owner;
			this.desc = desc;
		}

		@Override
		public int hashCode() {
			return this.name.hashCode() ^ this.owner.hashCode() ^ this.desc.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof MethodInvocationInfo) {
				MethodInvocationInfo other = (MethodInvocationInfo) obj;
				return other.name.equals(this.name) &&
						other.owner.equals(this.owner) &&
						other.desc.equals(this.desc);
			}

			return false;
		}
	}
}