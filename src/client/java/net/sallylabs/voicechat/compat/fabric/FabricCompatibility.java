package net.sallylabs.voicechat.compat.fabric;

import net.fabricmc.fabric.api.event.Event;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Bridges Fabric's buffer API (1.20.1) and payload API (1.20.5+)
 */
public final class FabricCompatibility {
    private FabricCompatibility() {}

    @SuppressWarnings("unchecked")
    public static void registerEvent(String owner, String field, Consumer<Object[]> callback)
            throws ReflectiveOperationException {
        Event<Object> event = (Event<Object>) Class.forName(owner).getField(field).get(null);
        Class<?> listener = event.invoker().getClass().getInterfaces()[0];
        event.register(
                proxy(
                        listener,
                        (object, method, args) -> {
                            callback.accept(args);
                            return null;
                        }));
    }

    public static void registerAlias(String path, Class<?> packetClass, Object channel)
            throws ReflectiveOperationException {
        Object nativeType = packetClass.getFields()[0].get(null);
        Method receive = named(channel.getClass(), "onClientPacket");
        Method decode = packetMethod(packetClass, "fromBytes");
        Class<?> networking =
                Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking");
        if (!nativeType.getClass().isRecord()) {
            Object id = identifier(nativeType.getClass(), path);
            Method register =
                    Arrays.stream(networking.getMethods())
                            .filter(
                                    m ->
                                            m.getName().equals("registerGlobalReceiver")
                                                    && m.getParameterTypes()[0].isInstance(id))
                            .findFirst()
                            .orElseThrow(() -> new NoSuchMethodException("Fabric buffer receiver"));
            Object listener =
                    proxy(
                            register.getParameterTypes()[1],
                            (object, method, args) -> {
                                Object packet =
                                        decode.invoke(
                                                packetClass.getConstructor().newInstance(),
                                                args[2]);
                                receive.invoke(channel, args[0], args[1], packet);
                                return null;
                            });
            register.invoke(null, id, listener);
            return;
        }
        Class<?> typeClass = nativeType.getClass();
        Class<?> identifierClass = typeClass.getRecordComponents()[0].getType();
        Object type = typeClass.getConstructors()[0].newInstance(identifier(identifierClass, path));
        Class<?> payloadClass = typeClass.getEnclosingClass();
        Class<?> registryClass =
                Class.forName("net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry");
        Method factory = null;
        for (Method method : registryClass.getMethods()) {
            if (method.getName().equals("clientboundPlay") || method.getName().equals("playS2C"))
                factory = method;
        }
        if (factory == null) throw new NoSuchMethodException("Fabric clientbound payload registry");
        Object registry = factory.invoke(null);
        Method registerCodec = named(registryClass, "register");
        Method encode = packetMethod(packetClass, "toBytes");
        Object codec =
                proxy(
                        registerCodec.getParameterTypes()[1],
                        (object, method, args) -> {
                            if (args.length == 1) {
                                Object packet =
                                        decode.invoke(
                                                packetClass.getConstructor().newInstance(),
                                                args[0]);
                                return proxy(payloadClass, new PayloadHandler(type, packet));
                            }
                            PayloadHandler payload =
                                    (PayloadHandler) Proxy.getInvocationHandler(args[1]);
                            encode.invoke(payload.packet, args[0]);
                            return null;
                        });
        registerCodec.invoke(registry, type, codec);
        Method registerReceiver =
                Arrays.stream(networking.getMethods())
                        .filter(
                                m ->
                                        m.getName().equals("registerGlobalReceiver")
                                                && m.getParameterTypes()[0].equals(typeClass))
                        .findFirst()
                        .orElseThrow(() -> new NoSuchMethodException("Fabric payload receiver"));
        Object listener =
                proxy(
                        registerReceiver.getParameterTypes()[1],
                        (object, method, args) -> {
                            PayloadHandler payload =
                                    (PayloadHandler) Proxy.getInvocationHandler(args[0]);
                            Method player = args[1].getClass().getMethod("player");
                            player.setAccessible(true);
                            receive.invoke(channel, player.invoke(args[1]), payload.packet);
                            return null;
                        });
        registerReceiver.invoke(null, type, listener);
    }

    private static Object identifier(Class<?> type, String path)
            throws ReflectiveOperationException {
        for (Method method : type.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())
                    && method.getReturnType() == type
                    && Arrays.equals(
                            method.getParameterTypes(),
                            new Class<?>[] {String.class, String.class})) {
                return method.invoke(null, "voicechat", path);
            }
        }
        return type.getConstructor(String.class, String.class).newInstance("voicechat", path);
    }

    public static Method packetMethod(Class<?> type, String name) throws NoSuchMethodException {
        return Arrays.stream(type.getMethods())
                .filter(m -> m.getName().equals(name) && !m.isBridge())
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(name));
    }

    private static Method named(Class<?> type, String name) throws NoSuchMethodException {
        return Arrays.stream(type.getMethods())
                .filter(m -> m.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new NoSuchMethodException(name));
    }

    private static Object proxy(Class<?> type, InvocationHandler callback) {
        return Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[] {type},
                (object, method, args) -> {
                    if (method.isDefault())
                        return InvocationHandler.invokeDefault(
                                object, method, args == null ? new Object[0] : args);
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "hashCode" -> System.identityHashCode(object);
                            case "equals" -> object == args[0];
                            default -> "Voice Chat compatibility " + type.getSimpleName();
                        };
                    }
                    return callback.invoke(object, method, args);
                });
    }

    private record PayloadHandler(Object type, Object packet) implements InvocationHandler {
        public Object invoke(Object proxy, Method method, Object[] args) {
            return type;
        }
    }
}
