package prisms.lang;

public class MathUtils {
	public static Number negate(Object value) {
		if(value instanceof Double)
			return -((Number) value).doubleValue();
		else if(value instanceof Float)
			return -((Number) value).floatValue();
		else if(value instanceof Long)
			return -((Number) value).longValue();
		else if(value instanceof Integer)
			return -((Number) value).intValue();
		else if(value instanceof Short)
			return -((Number) value).shortValue();
		else if(value instanceof Byte)
			return -((Number) value).byteValue();
		else if(value instanceof Character)
			return -((Character) value).charValue();
		else
			throw new IllegalStateException("Unrecognized number type");
	}

	public static Number complement(Object value) {
		if(value instanceof Long)
			return ~((Number) value).longValue();
		else if(value instanceof Integer)
			return ~((Number) value).intValue();
		else if(value instanceof Short)
			return ~((Number) value).shortValue();
		else if(value instanceof Byte)
			return ~((Number) value).byteValue();
		else if(value instanceof Character)
			return ~((Character) value).charValue();
		else
			throw new IllegalStateException("Unrecognized number type");
	}

	public static Number add(Type t1, Object v1, Type t2, Object v2) {
		if(t1.canAssignTo(Integer.TYPE) && t2.canAssignTo(Integer.TYPE)) {
			Type intType = new Type(Integer.TYPE);
			return ((Number) intType.cast(v1)).intValue() + ((Number) intType.cast(v2)).intValue();
		} else if(t1.canAssignTo(Long.TYPE) && t2.canAssignTo(Long.TYPE)) {
			Type longType = new Type(Long.TYPE);
			return ((Number) longType.cast(v1)).longValue() + ((Number) longType.cast(v2)).longValue();
		} else if(t1.canAssignTo(Float.TYPE) && t2.canAssignTo(Float.TYPE)) {
			Type floatType = new Type(Float.TYPE);
			return ((Number) floatType.cast(v1)).floatValue() + ((Number) floatType.cast(v2)).floatValue();
		} else {
			Type doubleType = new Type(Double.TYPE);
			return ((Number) doubleType.cast(v1)).doubleValue() + ((Number) doubleType.cast(v2)).doubleValue();
		}
	}

	public static Number subtract(Type t1, Object v1, Type t2, Object v2) {
		if(t1.canAssignTo(Integer.TYPE) && t2.canAssignTo(Integer.TYPE)) {
			Type intType = new Type(Integer.TYPE);
			return ((Number) intType.cast(v1)).intValue() - ((Number) intType.cast(v2)).intValue();
		} else if(t1.canAssignTo(Long.TYPE) && t2.canAssignTo(Long.TYPE)) {
			Type longType = new Type(Long.TYPE);
			return ((Number) longType.cast(v1)).longValue() - ((Number) longType.cast(v2)).longValue();
		} else if(t1.canAssignTo(Float.TYPE) && t2.canAssignTo(Float.TYPE)) {
			Type floatType = new Type(Float.TYPE);
			return ((Number) floatType.cast(v1)).floatValue() - ((Number) floatType.cast(v2)).floatValue();
		} else {
			Type doubleType = new Type(Double.TYPE);
			return ((Number) doubleType.cast(v1)).doubleValue() - ((Number) doubleType.cast(v2)).doubleValue();
		}
	}

	public static Number multiply(Type t1, Object v1, Type t2, Object v2) {
		if(t1.canAssignTo(Integer.TYPE) && t2.canAssignTo(Integer.TYPE)) {
			Type intType = new Type(Integer.TYPE);
			return ((Number) intType.cast(v1)).intValue() * ((Number) intType.cast(v2)).intValue();
		} else if(t1.canAssignTo(Long.TYPE) && t2.canAssignTo(Long.TYPE)) {
			Type longType = new Type(Long.TYPE);
			return ((Number) longType.cast(v1)).longValue() * ((Number) longType.cast(v2)).longValue();
		} else if(t1.canAssignTo(Float.TYPE) && t2.canAssignTo(Float.TYPE)) {
			Type floatType = new Type(Float.TYPE);
			return ((Number) floatType.cast(v1)).floatValue() * ((Number) floatType.cast(v2)).floatValue();
		} else {
			Type doubleType = new Type(Double.TYPE);
			return ((Number) doubleType.cast(v1)).doubleValue() * ((Number) doubleType.cast(v2)).doubleValue();
		}
	}

	public static Number divide(Type t1, Object v1, Type t2, Object v2) {
		if(t1.canAssignTo(Integer.TYPE) && t2.canAssignTo(Integer.TYPE)) {
			Type intType = new Type(Integer.TYPE);
			return ((Number) intType.cast(v1)).intValue() / ((Number) intType.cast(v2)).intValue();
		} else if(t1.canAssignTo(Long.TYPE) && t2.canAssignTo(Long.TYPE)) {
			Type longType = new Type(Long.TYPE);
			return ((Number) longType.cast(v1)).longValue() / ((Number) longType.cast(v2)).longValue();
		} else if(t1.canAssignTo(Float.TYPE) && t2.canAssignTo(Float.TYPE)) {
			Type floatType = new Type(Float.TYPE);
			return ((Number) floatType.cast(v1)).floatValue() / ((Number) floatType.cast(v2)).floatValue();
		} else {
			Type doubleType = new Type(Double.TYPE);
			return ((Number) doubleType.cast(v1)).doubleValue() / ((Number) doubleType.cast(v2)).doubleValue();
		}
	}

	public static Number modulo(Type t1, Object v1, Type t2, Object v2) {
		if(t1.canAssignTo(Integer.TYPE) && t2.canAssignTo(Integer.TYPE)) {
			Type intType = new Type(Integer.TYPE);
			return ((Number) intType.cast(v1)).intValue() % ((Number) intType.cast(v2)).intValue();
		} else if(t1.canAssignTo(Long.TYPE) && t2.canAssignTo(Long.TYPE)) {
			Type longType = new Type(Long.TYPE);
			return ((Number) longType.cast(v1)).longValue() % ((Number) longType.cast(v2)).longValue();
		} else if(t1.canAssignTo(Float.TYPE) && t2.canAssignTo(Float.TYPE)) {
			Type floatType = new Type(Float.TYPE);
			return ((Number) floatType.cast(v1)).floatValue() % ((Number) floatType.cast(v2)).floatValue();
		} else {
			Type doubleType = new Type(Double.TYPE);
			return ((Number) doubleType.cast(v1)).doubleValue() % ((Number) doubleType.cast(v2)).doubleValue();
		}
	}

	public static Number leftShift(Type t1, Object v1, Type t2, Object v2) {
		if(t1.canAssignTo(Integer.TYPE) && t2.canAssignTo(Integer.TYPE)) {
			Type intType = new Type(Integer.TYPE);
			return ((Number) intType.cast(v1)).intValue() << ((Number) intType.cast(v2)).intValue();
		} else {
			Type longType = new Type(Long.TYPE);
			return ((Number) longType.cast(t1)).longValue() << ((Number) longType.cast(v2)).longValue();
		}
	}

	public static Number rightShift(Type t1, Object v1, Type t2, Object v2) {
		if(t1.canAssignTo(Integer.TYPE) && t2.canAssignTo(Integer.TYPE)) {
			Type intType = new Type(Integer.TYPE);
			return ((Number) intType.cast(v1)).intValue() << ((Number) intType.cast(v2)).intValue();
		} else {
			Type longType = new Type(Long.TYPE);
			return ((Number) longType.cast(t1)).longValue() << ((Number) longType.cast(v2)).longValue();
		}
	}

	public static Number rightShiftUnsigned(Type t1, Object v1, Type t2, Object v2) {
		if(t1.canAssignTo(Integer.TYPE) && t2.canAssignTo(Integer.TYPE)) {
			Type intType = new Type(Integer.TYPE);
			return ((Number) intType.cast(v1)).intValue() << ((Number) intType.cast(v2)).intValue();
		} else {
			Type longType = new Type(Long.TYPE);
			return ((Number) longType.cast(t1)).longValue() << ((Number) longType.cast(v2)).longValue();
		}
	}
}
