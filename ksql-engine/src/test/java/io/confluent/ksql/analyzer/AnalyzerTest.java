/*
 * Copyright 2018 Confluent Inc.
 *
 * Licensed under the Confluent Community License; you may not use this file
 * except in compliance with the License.  You may obtain a copy of the License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.analyzer;

import static io.confluent.ksql.testutils.AnalysisTestUtil.analyzeQuery;
import static io.confluent.ksql.testutils.AnalysisTestUtil.getPreparedStatements;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import io.confluent.ksql.function.InternalFunctionRegistry;
import io.confluent.ksql.metastore.KsqlTopic;
import io.confluent.ksql.metastore.MetaStore;
import io.confluent.ksql.parser.KsqlParser.PreparedStatement;
import io.confluent.ksql.parser.SqlFormatter;
import io.confluent.ksql.metastore.StructuredDataSource;
import io.confluent.ksql.parser.tree.CreateStreamAsSelect;
import io.confluent.ksql.parser.tree.QualifiedName;
import io.confluent.ksql.parser.tree.QuerySpecification;
import io.confluent.ksql.parser.tree.Statement;
import io.confluent.ksql.parser.tree.Table;
import io.confluent.ksql.serde.json.KsqlJsonTopicSerDe;
import io.confluent.ksql.util.MetaStoreFixture;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class AnalyzerTest {

  private MetaStore metaStore;

  @Before
  public void init() {
    metaStore = MetaStoreFixture.getNewMetaStore(new InternalFunctionRegistry());
  }

  @Test
  public void testSimpleQueryAnalysis() {
    final String simpleQuery = "SELECT col0, col2, col3 FROM test1 WHERE col0 > 100;";
    final Analysis analysis = analyzeQuery(simpleQuery, metaStore);
    Assert.assertNotNull("INTO is null", analysis.getInto());
    Assert.assertNotNull("FROM is null", analysis.getFromDataSources());
    Assert.assertNotNull("SELECT is null", analysis.getSelectExpressions());
    Assert.assertNotNull("SELECT aliacs is null", analysis.getSelectExpressionAlias());
    Assert.assertTrue("FROM was not analyzed correctly.",
                      analysis.getFromDataSources().get(0).getLeft().getName()
                          .equalsIgnoreCase("test1"));
    Assert.assertTrue(
        analysis.getSelectExpressions().size() == analysis.getSelectExpressionAlias().size());
    final String
        sqlStr =
        SqlFormatter.formatSql(analysis.getWhereExpression()).replace("\n", " ");
    Assert.assertTrue(sqlStr.equalsIgnoreCase("(TEST1.COL0 > 100)"));

    final String
        select1 =
        SqlFormatter.formatSql(analysis.getSelectExpressions().get(0))
            .replace("\n", " ");
    Assert.assertTrue(select1.equalsIgnoreCase("TEST1.COL0"));
    final String
        select2 =
        SqlFormatter.formatSql(analysis.getSelectExpressions().get(1))
            .replace("\n", " ");
    Assert.assertTrue(select2.equalsIgnoreCase("TEST1.COL2"));
    final String
        select3 =
        SqlFormatter.formatSql(analysis.getSelectExpressions().get(2))
            .replace("\n", " ");
    Assert.assertTrue(select3.equalsIgnoreCase("TEST1.COL3"));

    Assert.assertTrue(analysis.getSelectExpressionAlias().get(0).equalsIgnoreCase("COL0"));
    Assert.assertTrue(analysis.getSelectExpressionAlias().get(1).equalsIgnoreCase("COL2"));
    Assert.assertTrue(analysis.getSelectExpressionAlias().get(2).equalsIgnoreCase("COL3"));
  }

  @Test
  public void testSimpleLeftJoinAnalysis() {
    final String
        simpleQuery =
        "SELECT t1.col1, t2.col1, t2.col4, col5, t2.col2 FROM test1 t1 LEFT JOIN test2 t2 ON "
        + "t1.col1 = t2.col1;";
    final Analysis analysis = analyzeQuery(simpleQuery, metaStore);
    Assert.assertNotNull("INTO is null", analysis.getInto());
    Assert.assertNotNull("JOIN is null", analysis.getJoin());

    Assert.assertNotNull("SELECT is null", analysis.getSelectExpressions());
    Assert.assertNotNull("SELECT aliacs is null", analysis.getSelectExpressionAlias());
    Assert.assertTrue("JOIN left hand side was not analyzed correctly.",
                      analysis.getJoin().getLeftAlias().equalsIgnoreCase("t1"));
    Assert.assertTrue("JOIN right hand side was not analyzed correctly.",
                      analysis.getJoin().getRightAlias().equalsIgnoreCase("t2"));

    Assert.assertTrue(
        analysis.getSelectExpressions().size() == analysis.getSelectExpressionAlias().size());

    Assert.assertTrue(analysis.getJoin().getLeftKeyFieldName().equalsIgnoreCase("COL1"));
    Assert.assertTrue(analysis.getJoin().getRightKeyFieldName().equalsIgnoreCase("COL1"));

    final String
        select1 =
        SqlFormatter.formatSql(analysis.getSelectExpressions().get(0))
            .replace("\n", " ");
    Assert.assertTrue(select1.equalsIgnoreCase("T1.COL1"));
    final String
        select2 =
        SqlFormatter.formatSql(analysis.getSelectExpressions().get(1))
            .replace("\n", " ");
    Assert.assertTrue(select2.equalsIgnoreCase("T2.COL1"));
    final String
        select3 =
        SqlFormatter.formatSql(analysis.getSelectExpressions().get(2))
            .replace("\n", " ");
    final String
        select4 =
        SqlFormatter.formatSql(analysis.getSelectExpressions().get(3))
            .replace("\n", " ");
    Assert.assertTrue(select3.equalsIgnoreCase("T2.COL4"));
    Assert.assertTrue(select4.equalsIgnoreCase("T1.COL5"));

    Assert.assertTrue(analysis.getSelectExpressionAlias().get(0).equalsIgnoreCase("T1_COL1"));
    Assert.assertTrue(analysis.getSelectExpressionAlias().get(1).equalsIgnoreCase("T2_COL1"));
    Assert.assertTrue(analysis.getSelectExpressionAlias().get(2).equalsIgnoreCase("T2_COL4"));
    Assert.assertTrue(analysis.getSelectExpressionAlias().get(3).equalsIgnoreCase("COL5"));
    Assert.assertTrue(analysis.getSelectExpressionAlias().get(4).equalsIgnoreCase("T2_COL2"));

  }

  @Test
  public void testBooleanExpressionAnalysis() {
    final String queryStr = "SELECT col0 = 10, col2, col3 > col1 FROM test1;";
    final Analysis analysis = analyzeQuery(queryStr, metaStore);

    Assert.assertNotNull("INTO is null", analysis.getInto());
    Assert.assertNotNull("FROM is null", analysis.getFromDataSources());
    Assert.assertNotNull("SELECT is null", analysis.getSelectExpressions());
    Assert.assertNotNull("SELECT aliacs is null", analysis.getSelectExpressionAlias());
    Assert.assertTrue("FROM was not analyzed correctly.",
                      analysis.getFromDataSources().get(0).getLeft().getName()
                          .equalsIgnoreCase("test1"));

    final String
        select1 =
        SqlFormatter.formatSql(analysis.getSelectExpressions().get(0))
            .replace("\n", " ");
    Assert.assertTrue(select1.equalsIgnoreCase("(TEST1.COL0 = 10)"));
    final String
        select2 =
        SqlFormatter.formatSql(analysis.getSelectExpressions().get(1))
            .replace("\n", " ");
    Assert.assertTrue(select2.equalsIgnoreCase("TEST1.COL2"));
    final String
        select3 =
        SqlFormatter.formatSql(analysis.getSelectExpressions().get(2))
            .replace("\n", " ");
    Assert.assertTrue(select3.equalsIgnoreCase("(TEST1.COL3 > TEST1.COL1)"));

  }

  @Test
  public void testFilterAnalysis() {
    final String queryStr = "SELECT col0 = 10, col2, col3 > col1 FROM test1 WHERE col0 > 20;";
    final Analysis analysis = analyzeQuery(queryStr, metaStore);

    Assert.assertNotNull("INTO is null", analysis.getInto());
    Assert.assertNotNull("FROM is null", analysis.getFromDataSources());
    Assert.assertNotNull("SELECT is null", analysis.getSelectExpressions());
    Assert.assertNotNull("SELECT aliacs is null", analysis.getSelectExpressionAlias());
    Assert.assertTrue("FROM was not analyzed correctly.",
            analysis.getFromDataSources().get(0).getLeft().getName()
                    .equalsIgnoreCase("test1"));

    final String
            select1 =
        SqlFormatter.formatSql(analysis.getSelectExpressions().get(0))
                    .replace("\n", " ");
    Assert.assertTrue(select1.equalsIgnoreCase("(TEST1.COL0 = 10)"));
    final String
            select2 =
        SqlFormatter.formatSql(analysis.getSelectExpressions().get(1))
                    .replace("\n", " ");
    Assert.assertTrue(select2.equalsIgnoreCase("TEST1.COL2"));
    final String
            select3 =
        SqlFormatter.formatSql(analysis.getSelectExpressions().get(2))
                    .replace("\n", " ");
    Assert.assertTrue(select3.equalsIgnoreCase("(TEST1.COL3 > TEST1.COL1)"));
    Assert.assertTrue("testFilterAnalysis failed.", analysis.getWhereExpression().toString().equalsIgnoreCase("(TEST1.COL0 > 20)"));

  }

  @Test
  public void shouldCreateCorrectSinkKsqlTopic() {
    final String simpleQuery = "CREATE STREAM FOO WITH (KAFKA_TOPIC='TEST_TOPIC1') AS SELECT col0, col2, col3 FROM test1 WHERE col0 > 100;";
    // The following few lines are only needed for this test
    final MetaStore testMetastore = metaStore.clone();
    final KsqlTopic ksqlTopic = new KsqlTopic("FOO", "TEST_TOPIC1", new KsqlJsonTopicSerDe(), true);
    testMetastore.putTopic(ksqlTopic);
    final List<Statement> statements = getPreparedStatements(simpleQuery, testMetastore)
        .stream()
        .map(PreparedStatement::getStatement)
        .collect(Collectors.toList());
    final CreateStreamAsSelect createStreamAsSelect = (CreateStreamAsSelect) statements.get(0);
    final Table intoTable = new Table(QualifiedName.of(createStreamAsSelect.getName().toString()));
    intoTable.setProperties(createStreamAsSelect.getProperties());
    final QuerySpecification querySpecification = (QuerySpecification) createStreamAsSelect.getQuery().getQueryBody();
    final QuerySpecification newQuerySpecification = new QuerySpecification(
        querySpecification.getSelect(),
        intoTable,
        false,
        querySpecification.getFrom(),
        querySpecification.getWindowExpression(),
        querySpecification.getWhere(),
        querySpecification.getGroupBy(),
        querySpecification.getHaving(),
        querySpecification.getLimit()
    );
    final Analysis analysis = new Analysis();
    final Analyzer analyzer = new Analyzer("sqlExpression", analysis, testMetastore, "");
    analyzer.visitQuerySpecification(newQuerySpecification, new AnalysisContext(null));

    Assert.assertNotNull("INTO is null", analysis.getInto());
    final StructuredDataSource structuredDataSource = analysis.getInto();
    final KsqlTopic createdKsqlTopic = structuredDataSource.getKsqlTopic();
    assertThat(createdKsqlTopic.getTopicName(), equalTo("FOO"));
    assertThat(createdKsqlTopic.getKafkaTopicName(), equalTo("TEST_TOPIC1"));
  }

}
