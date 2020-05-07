package buckelieg.jdbc.fn;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static buckelieg.jdbc.fn.Utils.STATEMENT_DELIMITER;
import static java.util.Arrays.stream;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

@ParametersAreNonnullByDefault
final class UpdateQueryDecorator implements Update {

    private boolean isLarge;
    private boolean isBatch;
    private int timeout;
    private TimeUnit unit = TimeUnit.SECONDS;
    private boolean isPoolable;
    private boolean skipWarnings;
    private boolean isEscaped;
    private final Connection connection;
    private final Object[][] batch;
    private final String query;
    private final String sql;

    UpdateQueryDecorator(Connection connection, String query, Object[]... batch) {
        this.connection = connection;
        this.query = requireNonNull(query, "SQL query must be provided");
        this.batch = requireNonNull(batch, "Batch must be provided");
        this.sql = stream(batch).map(p -> Utils.asSQL(query, p)).collect(joining(STATEMENT_DELIMITER));
    }

    @Nonnull
    @Override
    public Long execute() {
        return setParameters(new UpdateQuery(sql, connection, query, batch)).execute();
    }

    @Nonnull
    @Override
    public <T, K> T execute(TryFunction<ResultSet, K, SQLException> valueMapper, TryFunction<Stream<K>, T, SQLException> generatedValuesHandler) {
        return setParameters(new UpdateQuery(sql, (String[]) null, connection, query, batch)).execute(valueMapper, generatedValuesHandler);
    }

    @Nonnull
    @Override
    public <T, K> T execute(TryFunction<ResultSet, K, SQLException> valueMapper, TryFunction<Stream<K>, T, SQLException> generatedValuesHandler, String... colNames) {
        return setParameters(new UpdateQuery(sql, colNames, connection, query, batch)).execute(valueMapper, generatedValuesHandler);
    }

    @Nonnull
    @Override
    public <T, K> T execute(TryFunction<ResultSet, K, SQLException> valueMapper, TryFunction<Stream<K>, T, SQLException> generatedValuesHandler, int... colIndices) {
        return setParameters(new UpdateQuery(sql, colIndices, connection, query, batch)).execute(valueMapper, generatedValuesHandler);
    }

    @Override
    public Update large(boolean isLarge) {
        this.isLarge = isLarge;
        return this;
    }

    @Override
    public Update batched(boolean isBatch) {
        this.isBatch = isBatch;
        return this;
    }

    @Nonnull
    @Override
    public Update timeout(int timeout, TimeUnit unit) {
        this.timeout = timeout;
        this.unit = unit;
        return this;
    }

    @Nonnull
    @Override
    public Update poolable(boolean poolable) {
        this.isPoolable = poolable;
        return this;
    }

    @Nonnull
    @Override
    public Update escaped(boolean escapeProcessing) {
        this.isEscaped = escapeProcessing;
        return this;
    }

    @Nonnull
    @Override
    public Update skipWarnings(boolean skipWarnings) {
        this.skipWarnings = skipWarnings;
        return this;
    }

    @Nonnull
    @Override
    public Update print(Consumer<String> printer) {
        Objects.requireNonNull(printer, "Printer must be provided").accept(sql);
        return this;
    }

    @Nonnull
    @Override
    public String asSQL() {
        return sql;
    }

    private Update setParameters(Update update) {
        return update.timeout(timeout, unit).poolable(isPoolable).escaped(isEscaped).batched(isBatch).large(isLarge).skipWarnings(skipWarnings);
    }
}
