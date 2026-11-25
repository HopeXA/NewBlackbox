package top.niunaijun.blackbox.utils.compat;

import android.os.Parcel;
import android.os.Parcelable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

import black.android.content.pm.BRParceledListSlice;

public class ParceledListSliceCompat {

	public static boolean isReturnParceledListSlice(Method method) {
		return method != null && isParceledListSliceClass(method.getReturnType());
	}

	public static boolean isParceledListSlice(Object obj) {
		return obj != null && isParceledListSliceClass(obj.getClass());
	}

	private static boolean isParceledListSliceClass(Class<?> clazz) {
		if (clazz == null) {
			return false;
		}
		Class<?> baseClass = BRParceledListSlice.getRealClass();
		if (baseClass != null && baseClass.isAssignableFrom(clazz)) {
			return true;
		}
		try {
			Class<?> baseParceledClass = Class.forName("android.content.pm.BaseParceledListSlice");
			if (baseParceledClass.isAssignableFrom(clazz)) {
				return true;
			}
		} catch (Throwable ignored) {
		}
		return false;
	}

	public static Object create(List<?> list) {
		List<?> safeList = list != null ? list : Collections.emptyList();
		Object slice = BRParceledListSlice.get()._new(safeList);
		if (slice != null) {
			return slice;
		} else {
			slice = BRParceledListSlice.get()._new();
		}
		for (Object item : safeList) {
			BRParceledListSlice.get(slice).append(item);
		}
		BRParceledListSlice.get(slice).setLastSlice(true);
		return slice;
	}

	public static Object create(Method method, List<?> list) {
		List<?> safeList = list != null ? list : Collections.emptyList();
		if (method == null) {
			return create(safeList);
		}
		Class<?> returnType = method.getReturnType();
		Class<?> baseClass = BRParceledListSlice.getRealClass();
		if (returnType == null
				|| returnType == baseClass
				|| returnType == Object.class
				|| !isParceledListSliceClass(returnType)) {
			return create(safeList);
		}

		Object typedSlice = createTypedSlice(returnType, safeList);
		if (typedSlice != null) {
			return typedSlice;
		}
		return create(safeList);
	}

	private static Object createTypedSlice(Class<?> sliceClass, List<?> list) {
		// 1) Constructor(List) (e.g. PackageInfoList(List<PackageInfo>))
		try {
			Constructor<?> listCtor = sliceClass.getDeclaredConstructor(List.class);
			listCtor.setAccessible(true);
			return listCtor.newInstance(list);
		} catch (Throwable ignored) {
		}

		// 2) No-arg Constructor + append() + setLastSlice(true)
		try {
			Constructor<?> emptyCtor = sliceClass.getDeclaredConstructor();
			emptyCtor.setAccessible(true);
			Object slice = emptyCtor.newInstance();
			Class<?> baseClass = BRParceledListSlice.getRealClass();
			if (baseClass != null && baseClass.isInstance(slice)) {
				for (Object item : list) {
					BRParceledListSlice.get(slice).append(item);
				}
				BRParceledListSlice.get(slice).setLastSlice(true);
				return slice;
			}
			Method appendMethod = findMethod(sliceClass, "append", Object.class);
			Method setLastSliceMethod = findMethod(sliceClass, "setLastSlice", boolean.class);
			if (appendMethod != null && setLastSliceMethod != null) {
				appendMethod.setAccessible(true);
				setLastSliceMethod.setAccessible(true);
				for (Object item : list) {
					appendMethod.invoke(slice, item);
				}
				setLastSliceMethod.invoke(slice, true);
				return slice;
			}
		} catch (Throwable ignored) {
		}

		// 3) Parcel / CREATOR fallback
		Object baseSlice = create(list);
		if (baseSlice instanceof Parcelable) {
			Parcel parcel = Parcel.obtain();
			try {
				((Parcelable) baseSlice).writeToParcel(parcel, 0);
				parcel.setDataPosition(0);
				try {
					Field creatorField = sliceClass.getField("CREATOR");
					Object creator = creatorField.get(null);
					if (creator instanceof Parcelable.Creator) {
						return ((Parcelable.Creator<?>) creator).createFromParcel(parcel);
					}
				} catch (Throwable ignored) {
				}
				parcel.setDataPosition(0);
				try {
					Constructor<?> parcelLoaderCtor = sliceClass.getDeclaredConstructor(Parcel.class, ClassLoader.class);
					parcelLoaderCtor.setAccessible(true);
					return parcelLoaderCtor.newInstance(parcel, sliceClass.getClassLoader());
				} catch (Throwable ignored) {
				}
				parcel.setDataPosition(0);
				try {
					Constructor<?> parcelCtor = sliceClass.getDeclaredConstructor(Parcel.class);
					parcelCtor.setAccessible(true);
					return parcelCtor.newInstance(parcel);
				} catch (Throwable ignored) {
				}
			} catch (Throwable ignored) {
			} finally {
				parcel.recycle();
			}
		}

		return null;
	}

	private static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
		Class<?> current = clazz;
		while (current != null && current != Object.class) {
			try {
				return current.getDeclaredMethod(name, paramTypes);
			} catch (NoSuchMethodException ignored) {
				current = current.getSuperclass();
			}
		}
		return null;
	}
			}
