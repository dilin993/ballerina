package org.wso2.siddhi.core.util.parser;

import org.wso2.siddhi.core.ExecutionPlanRuntime;
import org.wso2.siddhi.core.config.ExecutionPlanContext;
import org.wso2.siddhi.core.event.MetaComplexEvent;
import org.wso2.siddhi.core.event.state.MetaStateEvent;
import org.wso2.siddhi.core.event.stream.MetaStreamEvent;
import org.wso2.siddhi.core.event.stream.populater.StreamEventPopulaterFactory;
import org.wso2.siddhi.core.exception.ExecutionPlanCreationException;
import org.wso2.siddhi.core.exception.ExecutionPlanRuntimeException;
import org.wso2.siddhi.core.executor.VariableExpressionExecutor;
import org.wso2.siddhi.core.query.input.stream.StreamRuntime;
import org.wso2.siddhi.core.query.selector.attribute.aggregator.incremental.ExecuteStreamReceiver;
import org.wso2.siddhi.core.query.selector.attribute.aggregator.incremental.IncrementalExecutor;
import org.wso2.siddhi.core.stream.input.source.Source;
import org.wso2.siddhi.core.stream.output.sink.Sink;
import org.wso2.siddhi.core.table.Table;
import org.wso2.siddhi.core.util.SiddhiConstants;
import org.wso2.siddhi.core.util.lock.LockSynchronizer;
import org.wso2.siddhi.core.util.lock.LockWrapper;
import org.wso2.siddhi.core.util.parser.helper.AggregationDefinitionParserHelper;
import org.wso2.siddhi.core.util.statistics.LatencyTracker;
import org.wso2.siddhi.core.window.Window;
import org.wso2.siddhi.query.api.aggregation.TimePeriod;
import org.wso2.siddhi.query.api.annotation.Element;
import org.wso2.siddhi.query.api.definition.AbstractDefinition;
import org.wso2.siddhi.query.api.definition.AggregationDefinition;
import org.wso2.siddhi.query.api.definition.Attribute;
import org.wso2.siddhi.query.api.exception.ExecutionPlanValidationException;
import org.wso2.siddhi.query.api.execution.query.input.stream.BasicSingleInputStream;
import org.wso2.siddhi.query.api.execution.query.input.stream.InputStream;
import org.wso2.siddhi.query.api.execution.query.input.stream.SingleInputStream;
import org.wso2.siddhi.query.api.execution.query.selection.OutputAttribute;
import org.wso2.siddhi.query.api.execution.query.selection.Selector;
import org.wso2.siddhi.query.api.expression.AttributeFunction;
import org.wso2.siddhi.query.api.expression.Expression;
import org.wso2.siddhi.query.api.expression.Variable;
import org.wso2.siddhi.query.api.util.AnnotationHelper;

import java.util.*;

public class AggregationParser {

    public static AggregationRuntime parse(AggregationDefinition definition,
                                           ExecutionPlanContext executionPlanContext,
                                           Map<String, AbstractDefinition> streamDefinitionMap,
                                           Map<String, AbstractDefinition> tableDefinitionMap,
                                           Map<String, AbstractDefinition> windowDefinitionMap,
                                           Map<String, Table> tableMap,
                                           Map<String, Window> windowMap,
                                           Map<String, List<Source>> eventSourceMap,
                                           Map<String, List<Sink>> eventSinkMap,
                                           LockSynchronizer lockSynchronizer) {

        // Read group by attribute
        // Check whether this is an INTERVAL or RANGE operation
        // Based on it, initialize Parent, child, child .... calculators
        // For each calculator
        // Create IncrementalCalculators
        // Calculate a set of unique base calculators
        // Initialize IncrementalStores with GroupBy attribute
        // When a new event comes
        //
        if (definition == null) {
            throw new ExecutionPlanCreationException("AggregationDefinition instance is null. " +
                    "Hence, can't create the execution plan");
        }
        if (definition.getTimePeriod() == null) {
            throw new ExecutionPlanCreationException("AggregationDefinition's timePeriod is null. " +
                    "Hence, can't create the execution plan");
        }
        if (definition.getSelector() == null) {
            throw new ExecutionPlanCreationException("AggregationDefinition's output attributes are not defined. " +
                    "Hence, can't create the execution plan");
        }

        List<VariableExpressionExecutor> executors = new ArrayList<VariableExpressionExecutor>();
        Element nameElement = null;
        LatencyTracker latencyTracker = null;
        LockWrapper lockWrapper = null;
        AggregationRuntime aggregationRuntime = null;
        try {
            nameElement = AnnotationHelper.getAnnotationElement("info", "name", definition.getAnnotations()); // TODO: 5/9/17 this gives null
            String aggregatorName = null; // TODO: 5/9/17 queryName or aggregatorName? Should Aggregator be accessible like this?
            if (nameElement != null) {
                aggregatorName = nameElement.getValue();
            } else {
                aggregatorName = "aggregator_" + UUID.randomUUID().toString(); // TODO: 5/9/17 "query_" or "aggregator_"
            }

            if (executionPlanContext.isStatsEnabled() && executionPlanContext.getStatisticsManager() != null) {
                if (nameElement != null) {
                    String metricName =
                            executionPlanContext.getSiddhiContext().getStatisticsConfiguration().getMatricPrefix() +
                                    SiddhiConstants.METRIC_DELIMITER + SiddhiConstants.METRIC_INFIX_EXECUTION_PLANS +
                                    SiddhiConstants.METRIC_DELIMITER + executionPlanContext.getName() +
                                    SiddhiConstants.METRIC_DELIMITER + SiddhiConstants.METRIC_INFIX_SIDDHI +
                                    SiddhiConstants.METRIC_DELIMITER + SiddhiConstants.METRIC_INFIX_QUERIES +
                                    SiddhiConstants.METRIC_DELIMITER + aggregatorName;
                    latencyTracker = executionPlanContext.getSiddhiContext()
                            .getStatisticsConfiguration()
                            .getFactory()
                            .createLatencyTracker(metricName, executionPlanContext.getStatisticsManager());
                }
            }

            InputStream inputStream = definition.getInputStream();
            StreamRuntime streamRuntime = InputStreamParser.parse(inputStream, executionPlanContext,
                    streamDefinitionMap, tableDefinitionMap, windowDefinitionMap, tableMap, windowMap,
                    executors, latencyTracker, false, aggregatorName);

            AbstractDefinition abstractDefinition = ((MetaStreamEvent) streamRuntime.getMetaComplexEvent()).getLastInputDefinition();


            List<OutputAttribute> outputAttributes = definition.getSelector().getSelectionList(); // TODO: 3/15/17 null checking ...
            List<AttributeFunction> functionsAttributes = new ArrayList<>();
            for (int i = 0; i < outputAttributes.size(); i++) {
                Expression expression = outputAttributes.get(i).getExpression();
                if (expression instanceof AttributeFunction) {
                    functionsAttributes.add((AttributeFunction) expression);
                }
            }

            List<TimePeriod.Duration> incrementalDurations = getSortedPeriods(definition.getTimePeriod());
            Variable groupByVar = getGroupByAttribute(definition.getSelector());


            //Make list of new attributes
            Set<Attribute> newMetaAttributes = createMetaAttributes(functionsAttributes, streamDefinitionMap, inputStream, groupByVar);

            /***Following is related to new MetaStreamEvent creation***/

            List<Attribute> attributeList = new ArrayList<>();

            //Create new metaStreamEvent corresponding to groupBy attribute and base aggregators
            MetaStreamEvent metaStreamEvent = new MetaStreamEvent();

            metaStreamEvent.addInputDefinition(abstractDefinition);

            metaStreamEvent.initializeAfterWindowData();
            for (Attribute newMetaAttribute : newMetaAttributes) {
                metaStreamEvent.addData(newMetaAttribute);
                attributeList.add(newMetaAttribute); // TODO: 5/24/17 Is this correct?
                executors.add(new VariableExpressionExecutor(newMetaAttribute,-1,0)); // TODO: 5/24/17 this adds new variable expression executor for groupBy too. but it's already there. Is it ok?
            }

            // TODO: 5/23/17 populate here?
            StreamEventPopulaterFactory.constructEventPopulator(metaStreamEvent,0,attributeList);
            /*******************************************/


            IncrementalExecutor child = build(functionsAttributes, incrementalDurations.get(incrementalDurations.size() - 1), null,
                    metaStreamEvent, 0, tableMap, executors, executionPlanContext,
                    true, 0, aggregatorName, groupByVar);
            IncrementalExecutor root;
            for (int i = incrementalDurations.size() - 2; i >= 0; i--) {
                root = build(functionsAttributes, incrementalDurations.get(i), child, metaStreamEvent, 0,
                        tableMap, executors, executionPlanContext, true, 0,
                        aggregatorName, groupByVar);
                child = root;
            }




            SingleInputStream singleInputStream = (SingleInputStream) inputStream;
            ExecuteStreamReceiver executeStreamReceiver = new ExecuteStreamReceiver(singleInputStream.getStreamId(), latencyTracker, aggregatorName);

            aggregationRuntime = new AggregationRuntime(definition, executionPlanContext, streamRuntime, metaStreamEvent, executeStreamReceiver);
            aggregationRuntime.setIncrementalExecutor(child);



            AggregationDefinitionParserHelper.reduceMetaComplexEvent(metaStreamEvent);
            AggregationDefinitionParserHelper.updateVariablePosition(metaStreamEvent, executors); // TODO: 5/22/17 change this logic
            AggregationDefinitionParserHelper.initStreamRuntime(streamRuntime, metaStreamEvent, lockWrapper, aggregatorName, aggregationRuntime);
        } catch (RuntimeException ex) {
            throw ex; // TODO: 5/12/17 should we log?
        }


//        MetaStreamEvent m=  new MetaStreamEvent();
//        new ComEve
        return aggregationRuntime;
    }

    private static IncrementalExecutor build(List<AttributeFunction> functionsAttributes,
                                             TimePeriod.Duration duration, IncrementalExecutor child, MetaComplexEvent metaEvent,
                                             int currentState, Map<String, Table> tableMap,
                                             List<VariableExpressionExecutor> executorList,
                                             ExecutionPlanContext executionPlanContext, boolean groupBy,
                                             int defaultStreamEventIndex, String queryName, Variable groupByVariable) {
        switch (duration) {
            case SECONDS:
                return IncrementalExecutor.second(functionsAttributes, child, metaEvent, currentState, tableMap,
                        executorList, executionPlanContext, groupBy, defaultStreamEventIndex, queryName, groupByVariable);
            case MINUTES:
                return IncrementalExecutor.minute(functionsAttributes, child, metaEvent, currentState, tableMap,
                        executorList, executionPlanContext, groupBy, defaultStreamEventIndex, queryName, groupByVariable);
            case HOURS:
                return IncrementalExecutor.hour(functionsAttributes, child, metaEvent, currentState, tableMap,
                        executorList, executionPlanContext, groupBy, defaultStreamEventIndex, queryName, groupByVariable);
            case DAYS:
                return IncrementalExecutor.day(functionsAttributes, child, metaEvent, currentState, tableMap,
                        executorList, executionPlanContext, groupBy, defaultStreamEventIndex, queryName, groupByVariable);
            case WEEKS:
                return IncrementalExecutor.week(functionsAttributes, child, metaEvent, currentState, tableMap,
                        executorList, executionPlanContext, groupBy, defaultStreamEventIndex, queryName, groupByVariable);
            case MONTHS:
                return IncrementalExecutor.month(functionsAttributes, child, metaEvent, currentState, tableMap,
                        executorList, executionPlanContext, groupBy, defaultStreamEventIndex, queryName, groupByVariable);
            case YEARS:
                return IncrementalExecutor.year(functionsAttributes, child, metaEvent, currentState, tableMap,
                        executorList, executionPlanContext, groupBy, defaultStreamEventIndex, queryName, groupByVariable);
            default:
                throw new EnumConstantNotPresentException(TimePeriod.Duration.class, "Aggregation is not defined for time period "+duration);
        }
    }

    private static Variable getGroupByAttribute(Selector selector) {
        // TODO: 5/12/17 null check done in line 62
        List<Variable> groupByAttributes = selector.getGroupByList();
        // TODO: 3/10/17 : Can we groupBy two or more attributes?

        // TODO: 3/10/17 : if groupByAttributes is empty throw an exception
        if (groupByAttributes.size() == 0) {
            throw new ExecutionPlanCreationException("Group by attribute must be provided to perform aggregation");
        }
        return groupByAttributes.get(0);
    }

    private static boolean isRange(TimePeriod timePeriod) {
        // TODO: 5/12/17 null check for timePeriod already done (line 58)
        if (timePeriod.getOperator() == TimePeriod.Operator.RANGE) {
            return true;
        }
        return false;
    }

    private static List<TimePeriod.Duration> getSortedPeriods(TimePeriod timePeriod) {
        // TODO: 5/12/17 null check for timePeriod already done (line 58)
        List<TimePeriod.Duration> durations = timePeriod.getDurations();
        if (isRange(timePeriod)) {
            durations = fillGap(durations.get(0), durations.get(1));
        }
        return sortedDurations(durations);
    }

    private static List<TimePeriod.Duration> sortedDurations(List<TimePeriod.Duration> durations) {
        List<TimePeriod.Duration> copyDurations = new ArrayList<TimePeriod.Duration>(durations);

        Comparator periodComparator = new Comparator<TimePeriod.Duration>() {
            public int compare(TimePeriod.Duration firstDuration, TimePeriod.Duration secondDuration) {
                int firstOrdinal = firstDuration.ordinal();
                int secondOrdinal = secondDuration.ordinal();
                if (firstOrdinal > secondOrdinal) {
                    return 1;
                } else if (firstOrdinal < secondOrdinal) {
                    return -1;
                }
                return 0;
            }
        };
        Collections.sort(copyDurations, periodComparator);
        return copyDurations;
    }

    private static List<TimePeriod.Duration> fillGap(TimePeriod.Duration start, TimePeriod.Duration end) {
        TimePeriod.Duration[] durations = TimePeriod.Duration.values();
        List<TimePeriod.Duration> filledDurations = new ArrayList<>();

        int startIndex = start.ordinal();
        int endIndex = end.ordinal();

        if (startIndex > endIndex) {
            throw new ExecutionPlanRuntimeException("Start time period must be less than end time period for range aggregation calculation");
        }

        if (startIndex == endIndex) {

            filledDurations.add(start);

        } else {
            TimePeriod.Duration[] temp = new TimePeriod.Duration[endIndex-startIndex+1];
            System.arraycopy(durations, startIndex, temp, 0, endIndex-startIndex+1);
            filledDurations = Arrays.asList(temp);
            /*for (int i = startIndex; i <= endIndex; i++) { // TODO: 3/10/17 : Array Copy ?
                filledDurations.add(durations[i]);
            }*/
        }
        return filledDurations;
    }

    private static Set<Attribute> createMetaAttributes(List<AttributeFunction> functionsAttributes, Map<String, AbstractDefinition> streamDefinitionMap, InputStream inputStream, Variable groupByVariable) {
        Set<Attribute> newMetaAttributes = new HashSet<>(); //Set used since only one meta is created per baseAggregator+attribute
        List<Attribute> currentAttributes = streamDefinitionMap.get(((SingleInputStream)inputStream).getStreamId()).getAttributeList();
        String attributeName = null;

        //Create map of current attribute names and types
        Map currentAttributeNameType = new HashMap();
        for (Attribute currentAttribute : currentAttributes) {
            currentAttributeNameType.put(currentAttribute.getName(), currentAttribute.getType());
        }

        //Add group by attribute to newMetaAttributes
        String groupByAttributeName = groupByVariable.getAttributeName();
        newMetaAttributes.add(new Attribute(groupByAttributeName,
                (Attribute.Type) currentAttributeNameType.get(groupByAttributeName)));

        //Create and add attributes related to base aggregate functions
        for (AttributeFunction attributeFunction: functionsAttributes) {
            if (attributeFunction.getParameters() == null) {
                throw new ExecutionPlanValidationException("Parameters cannot be null for an attribute function");
            }
            if (attributeFunction.getParameters().length != 1) {
                throw new ExecutionPlanValidationException("Only one parameter allowed for attribute function");
            }
            if (attributeFunction.getParameters()[0] == null || !(attributeFunction.getParameters()[0] instanceof Variable)) {
                throw new ExecutionPlanValidationException("Attribute function can only be executed on a variable");
            }
            attributeName = ((Variable) attributeFunction.getParameters()[0]).getAttributeName();


            switch (attributeFunction.getName()) {
                case "avg":
                    newMetaAttributes.add(new Attribute("sum".concat(attributeName),
                            (Attribute.Type) currentAttributeNameType.get(attributeName)));
                    newMetaAttributes.add(new Attribute("count".concat(attributeName),
                            (Attribute.Type) currentAttributeNameType.get(attributeName)));
                    break;
                case "sum":
                    newMetaAttributes.add(new Attribute("sum".concat(attributeName),
                            (Attribute.Type) currentAttributeNameType.get(attributeName)));
                    break;
                case "count":
                    newMetaAttributes.add(new Attribute("count".concat(attributeName),
                            (Attribute.Type) currentAttributeNameType.get(attributeName)));
                    break;
                    // TODO: 5/24/17 add other aggregates
                default:
                    throw new ExecutionPlanValidationException("Unknown attribute function");
            }
        }
        return newMetaAttributes;
    }
}
