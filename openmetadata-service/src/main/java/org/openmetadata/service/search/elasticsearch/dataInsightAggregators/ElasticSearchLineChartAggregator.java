package org.openmetadata.service.search.elasticsearch.dataInsightAggregators;

import es.org.elasticsearch.action.search.SearchRequest;
import es.org.elasticsearch.action.search.SearchResponse;
import es.org.elasticsearch.index.query.QueryBuilder;
import es.org.elasticsearch.index.query.RangeQueryBuilder;
import es.org.elasticsearch.search.aggregations.AbstractAggregationBuilder;
import es.org.elasticsearch.search.aggregations.Aggregation;
import es.org.elasticsearch.search.aggregations.AggregationBuilders;
import es.org.elasticsearch.search.aggregations.Aggregations;
import es.org.elasticsearch.search.aggregations.bucket.histogram.DateHistogramInterval;
import es.org.elasticsearch.search.aggregations.bucket.terms.IncludeExclude;
import es.org.elasticsearch.search.aggregations.bucket.terms.ParsedTerms;
import es.org.elasticsearch.search.aggregations.bucket.terms.Terms;
import es.org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import es.org.elasticsearch.search.builder.SearchSourceBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.openmetadata.common.utils.CommonUtil;
import org.openmetadata.schema.dataInsight.custom.DataInsightCustomChart;
import org.openmetadata.schema.dataInsight.custom.DataInsightCustomChartResult;
import org.openmetadata.schema.dataInsight.custom.DataInsightCustomChartResultList;
import org.openmetadata.schema.dataInsight.custom.FormulaHolder;
import org.openmetadata.schema.dataInsight.custom.LineChart;
import org.openmetadata.service.jdbi3.DataInsightSystemChartRepository;
import org.openmetadata.service.util.JsonUtils;

public class ElasticSearchLineChartAggregator
    implements ElasticSearchDynamicChartAggregatorInterface {
  public SearchRequest prepareSearchRequest(
      @NotNull DataInsightCustomChart diChart, long start, long end, List<FormulaHolder> formulas)
      throws IOException {
    LineChart lineChart = JsonUtils.convertValue(diChart.getChartDetails(), LineChart.class);
    AbstractAggregationBuilder aggregationBuilder;

    if (lineChart.getxAxisField() != null
        && !lineChart.getxAxisField().equals(DataInsightSystemChartRepository.TIMESTAMP_FIELD)) {
      aggregationBuilder =
          AggregationBuilders.terms("1").field(lineChart.getxAxisField()).size(1000);

      // in case of horizontal axis only process data of 24 hr prior to end time
      start = end - MILLISECONDS_IN_DAY;

    } else {
      aggregationBuilder =
          AggregationBuilders.dateHistogram("1")
              .field(DataInsightSystemChartRepository.TIMESTAMP_FIELD)
              .calendarInterval(DateHistogramInterval.DAY);
    }

    populateDateHistogram(
        lineChart.getFunction(),
        lineChart.getFormula(),
        lineChart.getField(),
        lineChart.getFilter(),
        aggregationBuilder,
        formulas);

    SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();

    QueryBuilder queryFilter =
        new RangeQueryBuilder(DataInsightSystemChartRepository.TIMESTAMP_FIELD).gte(start).lte(end);

    if (lineChart.getGroupBy() != null) {
      String[] includeArr = null;
      String[] excludeArr = null;
      if (!CommonUtil.nullOrEmpty(lineChart.getIncludeGroups())) {
        includeArr = lineChart.getIncludeGroups().toArray(new String[0]);
      }
      if (!CommonUtil.nullOrEmpty(lineChart.getExcludeGroups())) {
        excludeArr = lineChart.getExcludeGroups().toArray(new String[0]);
      }
      TermsAggregationBuilder termsAggregationBuilder =
          AggregationBuilders.terms("0").field(lineChart.getGroupBy()).size(1000);
      termsAggregationBuilder.subAggregation(aggregationBuilder);
      if (includeArr != null || excludeArr != null) {
        IncludeExclude includeExclude = new IncludeExclude(includeArr, excludeArr);
        termsAggregationBuilder.includeExclude(includeExclude);
      }
      searchSourceBuilder.size(0);
      searchSourceBuilder.aggregation(termsAggregationBuilder);
    } else {
      searchSourceBuilder.aggregation(aggregationBuilder);
    }
    searchSourceBuilder.query(queryFilter);
    es.org.elasticsearch.action.search.SearchRequest searchRequest =
        new es.org.elasticsearch.action.search.SearchRequest(
            DataInsightSystemChartRepository.DI_SEARCH_INDEX);
    searchRequest.source(searchSourceBuilder);
    return searchRequest;
  }

  public DataInsightCustomChartResultList processSearchResponse(
      @NotNull DataInsightCustomChart diChart,
      SearchResponse searchResponse,
      List<FormulaHolder> formulas) {
    DataInsightCustomChartResultList resultList = new DataInsightCustomChartResultList();
    LineChart lineChart = JsonUtils.convertValue(diChart.getChartDetails(), LineChart.class);
    List<Aggregation> aggregationList =
        Optional.ofNullable(searchResponse.getAggregations())
            .orElse(new Aggregations(new ArrayList<>()))
            .asList();
    if (lineChart.getGroupBy() != null) {
      List<DataInsightCustomChartResult> diChartResults = new ArrayList<>();
      for (Aggregation arg : aggregationList) {
        ParsedTerms parsedTerms = (ParsedTerms) arg;
        for (Terms.Bucket bucket : parsedTerms.getBuckets()) {
          diChartResults.addAll(
              processAggregations(
                  bucket.getAggregations().asList(),
                  lineChart.getFormula(),
                  bucket.getKeyAsString(),
                  formulas));
        }
      }
      resultList.setResults(diChartResults);
      return resultList;
    }
    List<DataInsightCustomChartResult> results =
        processAggregations(aggregationList, lineChart.getFormula(), null, formulas);
    resultList.setResults(results);
    if (lineChart.getKpiDetails() != null) {
      resultList.setKpiDetails(lineChart.getKpiDetails());
    }
    return resultList;
  }
}
