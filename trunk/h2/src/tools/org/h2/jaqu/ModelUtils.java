/*
 * Copyright 2004-2011 H2 Group. Multiple-Licensed under the H2 License,
 * Version 1.0, and under the Eclipse Public License, Version 1.0
 * (http://h2database.com/html/license.html).
 * Initial Developer: James Moger
 */
package org.h2.jaqu;

import static org.h2.jaqu.util.StringUtils.isNullOrEmpty;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.h2.jaqu.TableDefinition.FieldDefinition;

/**
 * Utility methods for models related to type mapping, default value validation,
 * and class or field name creation.
 */
public class ModelUtils {

    /**
     * The list of supported data types. It is used by the runtime mapping for
     * CREATE statements.
     */
    private static final Map<Class<?>, String> SUPPORTED_TYPES = new HashMap<Class<?>, String>();

    static {
        Map<Class<?>, String> m = SUPPORTED_TYPES;
        m.put(String.class, "VARCHAR");
        m.put(Boolean.class, "BIT");
        m.put(Byte.class, "TINYINT");
        m.put(Short.class, "SMALLINT");
        m.put(Integer.class, "INT");
        m.put(Long.class, "BIGINT");
        m.put(Float.class, "REAL");
        m.put(Double.class, "DOUBLE");
        m.put(BigDecimal.class, "DECIMAL");
        m.put(java.sql.Timestamp.class, "TIMESTAMP");
        m.put(java.util.Date.class, "TIMESTAMP");
        m.put(java.sql.Date.class, "DATE");
        m.put(java.sql.Time.class, "TIME");
        // TODO add blobs, binary types, custom types?
    }

    /**
     * Convert SQL type aliases to the list of supported types.
     * This map is used by generation and validation.
     */
    private static final Map<String, String> SQL_TYPES = new HashMap<String, String>();

    static {
        Map<String, String> m = SQL_TYPES;
        m.put("CHAR", "VARCHAR");
        m.put("CHARACTER", "VARCHAR");
        m.put("NCHAR", "VARCHAR");
        m.put("VARCHAR_CASESENSITIVE", "VARCHAR");
        m.put("VARCHAR_IGNORECASE", "VARCHAR");
        m.put("LONGVARCHAR", "VARCHAR");
        m.put("VARCHAR2", "VARCHAR");
        m.put("NVARCHAR", "VARCHAR");
        m.put("NVARCHAR2", "VARCHAR");
        m.put("TEXT", "VARCHAR");
        m.put("NTEXT", "VARCHAR");
        m.put("TINYTEXT", "VARCHAR");
        m.put("MEDIUMTEXT", "VARCHAR");
        m.put("LONGTEXT", "VARCHAR");
        m.put("CLOB", "VARCHAR");
        m.put("NCLOB", "VARCHAR");

        // logic
        m.put("BOOL", "BIT");
        m.put("BOOLEAN", "BIT");

        // numeric
        m.put("BYTE", "TINYINT");
        m.put("INT2", "SMALLINT");
        m.put("YEAR", "SMALLINT");
        m.put("INTEGER", "INT");
        m.put("MEDIUMINT", "INT");
        m.put("INT4", "INT");
        m.put("SIGNED", "INT");
        m.put("INT8", "BIGINT");
        m.put("IDENTITY", "BIGINT");

        // decimal
        m.put("NUMBER", "DECIMAL");
        m.put("DEC", "DECIMAL");
        m.put("NUMERIC", "DECIMAL");
        m.put("FLOAT", "DOUBLE");
        m.put("FLOAT4", "DOUBLE");
        m.put("FLOAT8", "DOUBLE");

        // date
        m.put("DATETIME", "TIMESTAMP");
        m.put("SMALLDATETIME", "TIMESTAMP");
    }

    private static final List<String> KEYWORDS = Arrays.asList("abstract", "assert", "boolean", "break", "byte", "case",
            "catch", "char", "class", "const", "continue", "default", "do", "double", "else", "enum", "extends",
            "final", "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface",
            "long", "native", "new", "package", "private", "protected", "public", "return", "short", "static",
            "strictfp", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "false", "null", "true");

    private int todoReviewWholeClass;

    /**
     * Returns a SQL type mapping for a Java class.
     *
     * @param field the field to map
     * @param strictTypeMapping throws a RuntimeException if type is unsupported
     * @return
     */
    public static String getDataType(FieldDefinition fieldDef, boolean strictTypeMapping) {
        Class<?> fieldClass = fieldDef.field.getType();
        if (SUPPORTED_TYPES.containsKey(fieldClass)) {
            String type = SUPPORTED_TYPES.get(fieldClass);
            if (type.equals("VARCHAR") && fieldDef.maxLength <= 0) {
                // Unspecified length strings are TEXT, not VARCHAR
                return "TEXT";
            }
            return type;
        }
        if (!strictTypeMapping) {
            return "VARCHAR";
        }
        throw new RuntimeException("Unsupported type " + fieldClass.getName());
    }

    /**
     * Returns the Java class type for a given SQL type.
     *
     * @param sqlType
     * @param dateClass the preferred date class (java.util.Date or
     *            java.sql.Timestamp)
     * @return
     */
    public static Class<?> getClassType(String sqlType,
            Class<? extends java.util.Date> dateClass) {
        sqlType = sqlType.toUpperCase();
        // TODO dropping "UNSIGNED" or parts like that could be trouble
        sqlType = sqlType.split(" ")[0].trim();

        if (SQL_TYPES.containsKey(sqlType)) {
            // convert the sqlType to a standard type
            sqlType = SQL_TYPES.get(sqlType);
        }
        Class<?> mappedClazz = null;
        for (Class<?> clazz : SUPPORTED_TYPES.keySet()) {
            if (SUPPORTED_TYPES.get(clazz).equalsIgnoreCase(sqlType)) {
                mappedClazz = clazz;
                break;
            }
        }
        if (mappedClazz != null) {
            if (mappedClazz.equals(java.util.Date.class)
                    || mappedClazz.equals(java.sql.Timestamp.class)) {
                return dateClass;
            }
            return mappedClazz;
        }
        return null;
    }

    /**
     * Tries to create a CamelCase class name from a table.
     *
     * @param name
     * @return
     */
    public static String createClassName(String name) {
        String[] chunks = name.split("_");
        StringBuilder newName = new StringBuilder();
        for (String chunk : chunks) {
            if (chunk.length() == 0) {
                // leading or trailing _
                continue;
            }
            newName.append(Character.toUpperCase(chunk.charAt(0)));
            newName.append(chunk.substring(1).toLowerCase());
        }
        return newName.toString();
    }

    /**
     * Ensures that table column names don't collide with Java keywords.
     *
     * @param col
     * @return
     */
    public static String createFieldName(String col) {
        String cn = col.toLowerCase();
        if (KEYWORDS.contains(cn)) {
            cn += "_value";
        }
        return cn;
    }

    /**
     * Checks the formatting of JQColumn.defaultValue()
     *
     * @param defaultValue
     * @return
     */
    public static boolean isProperlyFormattedDefaultValue(String defaultValue) {
        if (isNullOrEmpty(defaultValue)) {
            return true;
        }
        Pattern literalDefault = Pattern.compile("'.*'");
        Pattern functionDefault = Pattern.compile("[^'].*[^']");
        return literalDefault.matcher(defaultValue).matches()
            || functionDefault.matcher(defaultValue).matches();
    }

    /**
     * Checks to see if the defaultValue matches the Class.
     *
     * @param modelClazz
     * @param defaultValue
     * @return
     */
    public static boolean isValidDefaultValue(Class<?> modelClazz,
            String defaultValue) {

        if (defaultValue == null) {
            // NULL
            return true;
        }
        if (defaultValue.trim().length() == 0) {
            // NULL (effectively)
            return true;
        }

        // TODO H2 single-quotes literal values, which is useful.
        // MySQL does not single-quote literal values so its hard to
        // differentiate a FUNCTION/VARIABLE from a literal value.

        // function / variable
        Pattern functionDefault = Pattern.compile("[^'].*[^']");
        if (functionDefault.matcher(defaultValue).matches()) {
            // hard to validate this since its in the database
            // assume it is good
            return true;
        }

        // STRING
        if (modelClazz == String.class) {
            Pattern stringDefault = Pattern.compile("'(.|\\n)*'");
            return stringDefault.matcher(defaultValue).matches();
        }

        String dateRegex = "[0-9]{1,4}[-/\\.][0-9]{1,2}[-/\\.][0-9]{1,2}";
        String timeRegex = "[0-2]{1}[0-9]{1}:[0-5]{1}[0-9]{1}:[0-5]{1}[0-9]{1}";

        // TIMESTAMP
        if (modelClazz == java.util.Date.class
                || modelClazz == java.sql.Timestamp.class) {
            // this may be a little loose....
            // 00-00-00 00:00:00
            // 00/00/00T00:00:00
            // 00.00.00T00:00:00
            Pattern pattern = Pattern.compile("'" + dateRegex + "." + timeRegex + "'");
            return pattern.matcher(defaultValue).matches();
        }

        // DATE
        if (modelClazz == java.sql.Date.class) {
            // this may be a little loose....
            // 00-00-00
            // 00/00/00
            // 00.00.00
            Pattern pattern = Pattern.compile("'" + dateRegex + "'");
            return pattern.matcher(defaultValue).matches();
        }

        // TIME
        if (modelClazz == java.sql.Time.class) {
            // 00:00:00
            Pattern pattern = Pattern.compile("'" + timeRegex + "'");
            return pattern.matcher(defaultValue).matches();
        }

        // NUMBER
        if (Number.class.isAssignableFrom(modelClazz)) {
            // strip single quotes
            String unquoted = defaultValue;
            if (unquoted.charAt(0) == '\'') {
                unquoted = unquoted.substring(1);
            }
            if (unquoted.charAt(unquoted.length() - 1) == '\'') {
                unquoted = unquoted.substring(0, unquoted.length() - 1);
            }

            try {
                // delegate to static valueOf() method to parse string
                Method m = modelClazz.getMethod("valueOf", String.class);
                Object o = m.invoke(null, unquoted);
            } catch (NumberFormatException ex) {
                return false;
            } catch (Throwable t) {
            }
        }
        return true;
    }
}
