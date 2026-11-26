package top.niunaijun.blackbox.utils.compat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import black.android.content.pm.BRParceledListSlice;

public class ParceledListSliceCompat {

	public static boolean isReturnParceledListSlice(Method method) {
		return method != null && method.getReturnType() == BRParceledListSlice.getRealClass();
	}

	public static boolean isParceledListSlice(Object obj) {
		return obj != null && obj.getClass() == BRParceledListSlice.getRealClass();
	}

	public static Object create(List<?> list) {
		Object slice = BRParceledListSlice.get()._new(list);
		if (slice != null) {
			return slice;
		} else {
			slice = BRParceledListSlice.get()._new();
		}
		for (Object item : list) {
			BRParceledListSlice.get(slice).append(item);
		}
		BRParceledListSlice.get(slice).setLastSlice(true);
		return slice;
	}

	public static Object create(Method method, List<?> list) {
		if (method != null) {
			Class<?> returnType = method.getReturnType();
			Class<?> parceledClass = BRParceledListSlice.getRealClass();
			if (returnType != null && returnType != parceledClass && !List.class.isAssignableFrom(returnType)) {
				Object custom = createForType(returnType, list);
				if (custom != null) {
					return custom;
				}
			}
		}
		return create(list);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object createForType(Class<?> returnType, List<?> list) {
		try {
			Constructor<?> ctor = returnType.getDeclaredConstructor(List.class);
			ctor.setAccessible(true);
			return ctor.newInstance(list);
		} catch (Throwable ignored) {
		}
		try {
			Constructor<?> emptyCtor = returnType.getDeclaredConstructor();
			emptyCtor.setAccessible(true);
			Object instance = emptyCtor.newInstance();
			if (list != null) {
				try {
					Method append = returnType.getMethod("append", Object.class);
					for (Object item : list) {
						append.invoke(instance, item);
					}
					try {
						Method setLastSlice = returnType.getMethod("setLastSlice", boolean.class);
						setLastSlice.invoke(instance, true);
					} catch (Throwable ignored) {
					}
					return instance;
				} catch (Throwable ignored) {
				}
				try {
					Method getList = returnType.getMethod("getList");
					Object target = getList.invoke(instance);
					if (target instanceof List) {
						((List) target).addAll(list);
						return instance;
					}
				} catch (Throwable ignored) {
				}
				for (Class<?> c = returnType; c != null && c != Object.class; c = c.getSuperclass()) {
					for (Field f : c.getDeclaredFields()) {
						if (List.class.isAssignableFrom(f.getType())) {
							f.setAccessible(true);
							Object cur = f.get(instance);
							if (cur instanceof List) {
								((List) cur).addAll(list);
							} else {
								f.set(instance, list);
							}
							return instance;
						}
					}
				}
			}
			return instance;
		} catch (Throwable ignored) {
		}
		return null;
	}
}
