/*
 * Copyright 2024- Anatoly Kutyakov
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

import buckelieg.jdbc.fn.TryBiFunction;

import java.sql.SQLException;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static buckelieg.jdbc.Utils.newSQLRuntimeException;

final class SequentialSpliterator<T> implements Spliterator<T> {

  private final AtomicBoolean isInitialized = new AtomicBoolean();

  private final TryBiFunction<ValueReader, Integer, T, SQLException> mapper;

  final SelectQuery selectQuery;

  SequentialSpliterator(SelectQuery selectQuery, TryBiFunction<ValueReader, Integer, T, SQLException> mapper) {
	this.selectQuery = selectQuery;
	this.mapper = mapper;
  }

  @Override
  public boolean tryAdvance(Consumer<? super T> action) {
	T next = null;
	try {
	  if (!isInitialized.getAndSet(true)) {
		selectQuery.statement = selectQuery.prepareStatement();
		selectQuery.resultSet = selectQuery.doExecute(selectQuery.statement);
		selectQuery.currentResultSetNumber.incrementAndGet();
		if (selectQuery.resultSet != null) {
		  selectQuery.meta = new RSMeta(selectQuery.getConnection()::getMetaData, selectQuery.resultSet::getMetaData, selectQuery.metaCache);
		  selectQuery.wrapper = ValueGetters.reader(selectQuery.meta, selectQuery.resultSet);
		} else {
		  selectQuery.finisher.run();
		  selectQuery.close();
		  return false;
		}
	  }
	  if (selectQuery.resultSet.next()) next = mapper.apply(selectQuery.wrapper, selectQuery.currentResultSetNumber.get());
	  else if (selectQuery.statement.getMoreResults()) {
		selectQuery.resultSet = selectQuery.statement.getResultSet();
		selectQuery.meta = new RSMeta(selectQuery.getConnection()::getMetaData, selectQuery.resultSet::getMetaData, selectQuery.metaCache);
		selectQuery.currentResultSetNumber.incrementAndGet();
		selectQuery.wrapper = ValueGetters.reader(selectQuery.meta, selectQuery.resultSet);
		if (selectQuery.resultSet.next()) next = mapper.apply(selectQuery.wrapper, selectQuery.currentResultSetNumber.get());
	  } else {
		selectQuery.finisher.run();
		return false;
	  }
	  action.accept(next);
	  return true;
	} catch (SQLException e) {
	  throw newSQLRuntimeException(e);
	}
  }

  @Override
  public Spliterator<T> trySplit() {
	return null;
  }

  @Override
  public long estimateSize() {
	return Long.MAX_VALUE;
  }

  @Override
  public int characteristics() {
	return IMMUTABLE | ORDERED;
  }

}
