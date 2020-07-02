/*
 * Copyright 2016- Anatoly Kutyakov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package buckelieg.jdbc;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.sql.JDBCType;
import java.sql.ParameterMetaData;
import java.sql.SQLType;
import java.util.Objects;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * Stored Procedure parameter
 *
 * @param <T> parameter value type
 * @see StoredProcedure
 * @see java.sql.ParameterMetaData
 */
@ParametersAreNonnullByDefault
public final class P<T> {

    private final int mode;
    private final String name;
    private final T value;
    private final SQLType type;

    private P(int mode, SQLType type, String name, @Nullable T value) {
        this.mode = mode;
        this.type = requireNonNull(type, "Parameter type must be provided");
        this.name = requireNonNull(name, "Parameter name must be provided");
        this.value = value;
    }

    /**
     * Creates a {@link StoredProcedure} <code>IN</code> parameter
     *
     * @param type  parameter type
     * @param name  parameter name
     * @param value parameter value
     * @param <T>   parameter value type
     * @return a {@link StoredProcedure} <code>IN</code> parameter
     * @see ParameterMetaData#parameterModeIn
     */
    public static <T> P<T> in(SQLType type, String name, T value) {
        return new P<>(ParameterMetaData.parameterModeIn, type, name, value);
    }

    /**
     * Creates an anonymous {@link StoredProcedure} <code>IN</code> parameter with {@link JDBCType#JAVA_OBJECT} type
     *
     * @param value parameter value
     * @param <T>   parameter value type
     * @return a {@link StoredProcedure} <code>IN</code> parameter
     * @see ParameterMetaData#parameterModeIn
     */
    public static <T> P<T> in(T value) {
        return in(JDBCType.JAVA_OBJECT, "", value);
    }

    /**
     * Creates a {@link StoredProcedure} <code>IN</code> parameter
     *
     * @param type  parameter type
     * @param value parameter value
     * @param <T>   parameter value type
     * @return a {@link StoredProcedure} <code>IN</code> parameter
     * @see ParameterMetaData#parameterModeIn
     */
    public static <T> P<T> in(SQLType type, T value) {
        return in(type, "", value);
    }

    /**
     * Creates a {@link StoredProcedure} <code>IN</code> parameter with {@link JDBCType#JAVA_OBJECT} type
     *
     * @param name  parameter name
     * @param value parameter value
     * @param <T>   parameter value type
     * @return a {@link StoredProcedure} <code>IN</code> parameter
     * @see ParameterMetaData#parameterModeIn
     */
    public static <T> P<T> in(String name, T value) {
        return in(JDBCType.JAVA_OBJECT, name, value);
    }

    /**
     * Creates a {@link StoredProcedure} <code>OUT</code> parameter
     *
     * @param type parameter type
     * @param name parameter name
     * @param <T>  parameter value type
     * @return a {@link StoredProcedure} <code>OUT</code> parameter
     * @see ParameterMetaData#parameterModeOut
     */
    public static <T> P<T> out(SQLType type, String name) {
        return new P<>(ParameterMetaData.parameterModeOut, type, name, null);
    }

    /**
     * Creates a {@link StoredProcedure} <code>OUT</code> parameter
     *
     * @param type parameter type
     * @param <T>  parameter value type
     * @return a {@link StoredProcedure} <code>OUT</code> parameter
     * @see ParameterMetaData#parameterModeOut
     */
    public static <T> P<T> out(SQLType type) {
        return out(type, "");
    }

    /**
     * Creates a {@link StoredProcedure} <code>OUT</code> parameter with {@link JDBCType#JAVA_OBJECT} type
     *
     * @param name parameter name
     * @param <T>  parameter value type
     * @return a {@link StoredProcedure} <code>OUT</code> parameter
     * @see ParameterMetaData#parameterModeOut
     */
    public static <T> P<T> out(String name) {
        return out(JDBCType.JAVA_OBJECT, name);
    }

    /**
     * Creates a {@link StoredProcedure} <code>OUT</code> parameter
     *
     * @param type  parameter type
     * @param name  parameter name
     * @param value parameter value
     * @param <T>   parameter value type
     * @return a {@link StoredProcedure} <code>OUT</code> parameter
     * @see ParameterMetaData#parameterModeOut
     */
    public static <T> P<T> inOut(SQLType type, String name, T value) {
        return new P<>(ParameterMetaData.parameterModeInOut, type, name, value);
    }

    /**
     * Creates a {@link StoredProcedure} <code>OUT</code> parameter
     *
     * @param type  parameter type
     * @param value parameter value
     * @param <T>   parameter value type
     * @return a {@link StoredProcedure} <code>OUT</code> parameter
     * @see ParameterMetaData#parameterModeOut
     */
    public static <T> P<T> inOut(SQLType type, T value) {
        return inOut(type, "", value);
    }

    /**
     * Creates a {@link StoredProcedure} <code>INOUT</code> parameter
     *
     * @param name  parameter name
     * @param value parameter value
     * @param <T>   parameter value type
     * @return a {@link StoredProcedure} <code>INOUT</code> parameter
     * @see ParameterMetaData#parameterModeInOut
     */
    public static <T> P<T> inOut(String name, T value) {
        return inOut(JDBCType.JAVA_OBJECT, name, value);
    }

    /**
     * Creates a {@link StoredProcedure} <code>INOUT</code> parameter with {@link JDBCType#JAVA_OBJECT} type
     *
     * @param value parameter value
     * @param <T>   parameter value type
     * @return a {@link StoredProcedure} <code>INOUT</code> parameter
     * @see ParameterMetaData#parameterModeInOut
     */
    public static <T> P<T> inOut(T value) {
        return inOut(JDBCType.JAVA_OBJECT, "", value);
    }

    /**
     * Test if this is an <code>IN</code> parameter
     *
     * @return true if this is <code>IN</code>, false - otherwise
     * @see ParameterMetaData#parameterModeIn
     */
    public boolean isIn() {
        return mode == ParameterMetaData.parameterModeIn;
    }

    /**
     * Test if this is an <code>OUT</code> parameter
     *
     * @return true if this is <code>OUT</code>, false - otherwise
     * @see ParameterMetaData#parameterModeOut
     */
    public boolean isOut() {
        return mode == ParameterMetaData.parameterModeOut;
    }

    /**
     * Test if this is an <code>INOUT</code> parameter
     *
     * @return true if this is <code>INOUT</code> parameter, false - otherwise
     * @see ParameterMetaData#parameterModeInOut
     */
    public boolean isInOut() {
        return mode == ParameterMetaData.parameterModeInOut;
    }

    /**
     * Returns the name of this parameter
     *
     * @return parameter name
     */
    @Nonnull
    public String getName() {
        return name;
    }

    /**
     * Returns the value of this parameter
     *
     * @return parameter value
     */
    @Nullable
    public T getValue() {
        return value;
    }

    /**
     * Returns an {@link SQLType} of this parameter
     *
     * @return parameter type
     */
    @Nullable
    public SQLType getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        P<?> p = (P<?>) o;

        if (mode != p.mode) return false;
        if (!name.equals(p.name)) return false;
        if (!Objects.equals(value, p.value)) return false;
        return type.equals(p.type);
    }

    @Override
    public int hashCode() {
        int result = mode;
        result = 31 * result + name.hashCode();
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + type.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return format("%s:%s=%s(%s)", isInOut() ? "INOUT" : isOut() ? "OUT" : "IN", name, value, type.getName());
    }
}
