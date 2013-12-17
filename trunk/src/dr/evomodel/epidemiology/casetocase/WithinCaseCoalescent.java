package dr.evomodel.epidemiology.casetocase;

import dr.evolution.coalescent.Coalescent;
import dr.evolution.coalescent.DemographicFunction;
import dr.evolution.coalescent.IntervalList;
import dr.evolution.coalescent.IntervalType;
import dr.evolution.tree.FlexibleNode;
import dr.evolution.tree.FlexibleTree;
import dr.evolution.tree.NodeRef;
import dr.evolution.tree.Tree;
import dr.evolution.util.Date;
import dr.evolution.util.Taxa;
import dr.evolution.util.Taxon;
import dr.evolution.util.TaxonList;
import dr.evomodel.coalescent.DemographicModel;
import dr.evomodel.tree.TreeModel;
import dr.inference.distribution.ParametricDistributionModel;
import dr.inference.loggers.LogColumn;
import dr.inference.model.Model;
import dr.inference.model.Parameter;
import dr.inference.model.Variable;
import dr.math.*;
import dr.xml.*;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

/**
 * Intended to replace the tree prior; each partition is considered a tree in its own right generated by a
 * coalescent process
 *
 * @author Matthew Hall
 */

public class WithinCaseCoalescent extends CaseToCaseTreeLikelihood {

    public static final String WITHIN_CASE_COALSECENT = "withinCaseCoalescent";
    private Double[] partitionTreeLogLikelihoods;
    private Double[] storedPartitionTreeLogLikelihoods;
    private Double[] timingLogLikelihoods;
    private Double[] storedTimingLogLikelihoods;
    private TreePlusRootBranchLength[] partitionsAsTrees;
    private TreePlusRootBranchLength[] storedPartitionsAsTrees;
    private DemographicModel demoModel;


    public WithinCaseCoalescent(TreeModel virusTree, AbstractOutbreak caseData, String startingNetworkFileName,
                                     Parameter infectionTimeBranchPositions, Parameter maxFirstInfToRoot,
                                     DemographicModel demoModel)
            throws TaxonList.MissingTaxonException {
        super(WITHIN_CASE_COALSECENT, virusTree, caseData, startingNetworkFileName, infectionTimeBranchPositions, null,
                maxFirstInfToRoot);
        this.demoModel = demoModel;
        partitionTreeLogLikelihoods = new Double[noTips];
        storedPartitionTreeLogLikelihoods = new Double[noTips];
        timingLogLikelihoods = new Double[noTips];
        storedTimingLogLikelihoods = new Double[noTips];

        partitionsAsTrees = new TreePlusRootBranchLength[caseData.size()];
        explodeTree();
        storedPartitionsAsTrees = new TreePlusRootBranchLength[caseData.size()];
    }

    public WithinCaseCoalescent(TreeModel virusTree, AbstractOutbreak caseData, String startingNetworkFileName,
                                Parameter infectionTimeBranchPositions, Parameter infectiousTimePositions,
                                Parameter maxFirstInfToRoot, DemographicModel demoModel)
            throws TaxonList.MissingTaxonException {
        super(WITHIN_CASE_COALSECENT, virusTree, caseData, startingNetworkFileName, infectionTimeBranchPositions,
                infectiousTimePositions, maxFirstInfToRoot);
        this.demoModel = demoModel;
        partitionTreeLogLikelihoods = new Double[noTips];
        storedPartitionTreeLogLikelihoods = new Double[noTips];
        timingLogLikelihoods = new Double[noTips];
        storedTimingLogLikelihoods = new Double[noTips];

        partitionsAsTrees = new TreePlusRootBranchLength[caseData.size()];
        explodeTree();
        storedPartitionsAsTrees = new TreePlusRootBranchLength[caseData.size()];
    }

    protected double calculateLogLikelihood(){

        super.prepareTimings();
        explodeTree();

        // if the prior on the TT structure is non-informative, then we can just use Cayley's formula with an
        // additional noTips multiplication for the choice of root:

        double logL = -Math.log(Math.pow(noTips, noTips-1));

        for(int i=0; i<noTips; i++){
            // the terms for infectious (and latent, if present) periods come from the parent class, without normalisation
            AbstractCase aCase = cases.getCase(i);
            AbstractCase parent = getInfector(aCase);
            HashSet<AbstractCase> children = getInfectees(aCase);
            if(timingLogLikelihoods[i]==null){

                double latestInfectiousTime = aCase.getCullTime();
                double infectionTime = getInfectionTime(aCase);
                for(AbstractCase child : children){
                    double childInfTime = getInfectionTime(child);
                    if(childInfTime<latestInfectiousTime){
                        latestInfectiousTime = childInfTime;
                    }
                }
                if(parent!=null){
                    double latestInfectionTime = parent.getCullTime();
                    if(!hasLatentPeriods){
                        timingLogLikelihoods[i] = Math.log(((WithinCaseCategoryOutbreak) cases).infectedAtGivenBefore
                                (i, infectionTime, Math.min(latestInfectionTime, latestInfectiousTime)));
                    } else {
                        double infectiousTime = getInfectiousTime(aCase);
                        timingLogLikelihoods[i] = Math.log(((WithinCaseCategoryOutbreak) cases)
                                .infectedAtGivenBeforeInfectiousAtGivenBefore(i, infectionTime, latestInfectionTime,
                                        infectiousTime, latestInfectiousTime));
                    }
                } else {
                    if(!hasLatentPeriods){
                        timingLogLikelihoods[i] = Math.log(((WithinCaseCategoryOutbreak)cases).infectedAtGivenBefore
                                (i, infectionTime, latestInfectiousTime));
                    } else {
                        double infectiousTime = getInfectiousTime(aCase);
                        timingLogLikelihoods[i] = Math.log(((WithinCaseCategoryOutbreak)cases)
                                .infectedAtGivenBeforeInfectiousAtGivenBefore(i, infectionTime, infectiousTime,
                                        infectiousTime, latestInfectiousTime));
                    }
                }
                logL += timingLogLikelihoods[i];
            }

            // and then the little tree calculations

            if(partitionTreeLogLikelihoods[i]==null){
                TreePlusRootBranchLength treePlus = partitionsAsTrees[i];
                if(children.size()!=0){
                    MaxTMRCACoalescent coalescent = new MaxTMRCACoalescent(treePlus, demoModel,
                        treePlus.getRootHeight()+treePlus.getRootBranchLength());
                    partitionTreeLogLikelihoods[i] = coalescent.calculateLogLikelihood();
                    logL += partitionTreeLogLikelihoods[i];
                } else {
                    partitionTreeLogLikelihoods[i] = 0.0;
                }
            } else {
                logL += partitionTreeLogLikelihoods[i];
            }
        }

        return logL;
    }


    public void storeState(){
        super.storeState();
        storedPartitionsAsTrees = Arrays.copyOf(partitionsAsTrees, partitionsAsTrees.length);
        storedPartitionTreeLogLikelihoods = Arrays.copyOf(storedPartitionTreeLogLikelihoods,
                storedPartitionTreeLogLikelihoods.length);
        storedTimingLogLikelihoods = Arrays.copyOf(timingLogLikelihoods, timingLogLikelihoods.length);
    }

    public void restoreState(){
        super.restoreState();
        partitionsAsTrees = storedPartitionsAsTrees;
        partitionTreeLogLikelihoods = storedPartitionTreeLogLikelihoods;
        timingLogLikelihoods = storedTimingLogLikelihoods;
    }

    protected void handleModelChangedEvent(Model model, Object object, int index) {

        super.handleModelChangedEvent(model, object, index);

        if(model == treeModel){
            Arrays.fill(partitionTreeLogLikelihoods, null);
            Arrays.fill(partitionsAsTrees, null);
        }
        Arrays.fill(timingLogLikelihoods, null);

    }



    public void changeMap(int node, AbstractCase partition){
        super.changeMap(node, partition);
    }

    // Tears the tree into small pieces. Indexes correspond to indexes in the outbreak.
    // todo Work out when components of this are unchanged after PT or TT moves

    private void explodeTree(){
        for(int i=0; i<cases.size(); i++){
            if(partitionsAsTrees[i]==null){
                AbstractCase aCase = cases.getCase(i);

                NodeRef partitionRoot = getEarliestNodeInPartition(aCase);

                double infectionTime = getInfectionTime(branchMap[partitionRoot.getNumber()]);
                double rootTime = getNodeTime(partitionRoot);

                FlexibleNode newRoot = new FlexibleNode();

                FlexibleTree littleTree = new FlexibleTree(newRoot);

                if(!treeModel.isExternal(partitionRoot)){
                    for(int j=0; j<treeModel.getChildCount(partitionRoot); j++){
                        copyPartitionToLittleTree(littleTree, treeModel.getChild(partitionRoot, j), newRoot, aCase);
                    }
                }

                partitionsAsTrees[i] = new TreePlusRootBranchLength(littleTree, rootTime - infectionTime);
            }
        }
    }

    private void copyPartitionToLittleTree(FlexibleTree littleTree, NodeRef oldNode, NodeRef newParent,
                                           AbstractCase partition){
        if(branchMap[oldNode.getNumber()]==partition){
            if(treeModel.isExternal(oldNode)){
                NodeRef newTip = new FlexibleNode(new Taxon(treeModel.getNodeTaxon(oldNode).getId()));
                littleTree.addChild(newParent, newTip);
                littleTree.setBranchLength(newTip, treeModel.getBranchLength(oldNode));
            } else {
                NodeRef newChild = new FlexibleNode();
                littleTree.addChild(newParent, newChild);
                littleTree.setBranchLength(newChild, treeModel.getBranchLength(oldNode));
                for(int i=0; i<treeModel.getChildCount(oldNode); i++){
                    copyPartitionToLittleTree(littleTree, treeModel.getChild(oldNode, i), newChild, partition);
                }
            }
        } else {
            // we need a new tip
            NodeRef transmissionTip = new FlexibleNode(
                    new Taxon("Transmission_"+branchMap[oldNode.getNumber()].caseID));
            double parentTime = getNodeTime(treeModel.getParent(oldNode));
            double childTime = getInfectionTime(branchMap[oldNode.getNumber()]);
            littleTree.addChild(newParent, transmissionTip);
            littleTree.setBranchLength(transmissionTip, childTime - parentTime);

        }
    }

    private class TreePlusRootBranchLength extends FlexibleTree {

        private double rootBranchLength;

        private TreePlusRootBranchLength(FlexibleTree tree, double rootBranchLength){
            super(tree);
            this.rootBranchLength = rootBranchLength;
        }

        private double getRootBranchLength(){
            return rootBranchLength;
        }

        private void setRootBranchLength(){
            this.rootBranchLength = rootBranchLength;
        }
    }

    private class MaxTMRCACoalescent extends Coalescent {

        private double maxHeight;

        private MaxTMRCACoalescent(Tree tree, DemographicModel demographicModel, double maxHeight){
            super(tree, demographicModel.getDemographicFunction());
            this.maxHeight = maxHeight;
        }

        public double calculateLogLikelihood() {
            return calculatePartitionTreeLogLikelihood(getIntervals(), getDemographicFunction(), 0, maxHeight);
        }

    }

    public static double calculatePartitionTreeLogLikelihood(IntervalList intervals,
                                                             DemographicFunction demographicFunction, double threshold,
                                                             double maxHeight) {

        double logL = 0.0;

        double startTime = 0.0;
        final int n = intervals.getIntervalCount();
        for (int i = 0; i < n; i++) {

            final double duration = intervals.getInterval(i);
            final double finishTime = startTime + duration;

            final double intervalArea = demographicFunction.getIntegral(startTime, finishTime);
            double normalisationArea = demographicFunction.getIntegral(startTime, maxHeight);

            if( intervalArea == 0 && duration != 0 ) {
                return Double.NEGATIVE_INFINITY;
            }
            final int lineageCount = intervals.getLineageCount(i);

            final double kChoose2 = Binomial.choose2(lineageCount);

            if (intervals.getIntervalType(i) == IntervalType.COALESCENT) {

                logL += -kChoose2 * intervalArea;

                final double demographicAtCoalPoint = demographicFunction.getDemographic(finishTime);

                // if value at end is many orders of magnitude different than mean over interval reject the interval
                // This is protection against cases where ridiculous infitisimal
                // population size at the end of a linear interval drive coalescent values to infinity.

                if( duration == 0.0 || demographicAtCoalPoint * (intervalArea/duration) >= threshold ) {
                    logL -= Math.log(demographicAtCoalPoint);
                } else {
                    // remove this at some stage
                    return Double.NEGATIVE_INFINITY;
                }

            } else {
                double numerator = Math.exp(kChoose2 * intervalArea) - Math.exp(kChoose2 * normalisationArea);
                logL += Math.log(numerator);

            }

            // normalisation

            double denominator = 1-Math.exp(kChoose2 * normalisationArea);

            logL -= Math.log(denominator);

            startTime = finishTime;
        }

        return logL;
    }

    //************************************************************************
    // AbstractXMLObjectParser implementation
    //************************************************************************

    public static XMLObjectParser PARSER = new AbstractXMLObjectParser() {
        public static final String STARTING_NETWORK = "startingNetwork";
        public static final String INFECTION_TIMES = "infectionTimeBranchPositions";
        public static final String INFECTIOUS_TIMES = "infectiousTimePositions";
        public static final String MAX_FIRST_INF_TO_ROOT = "maxFirstInfToRoot";
        public static final String DEMOGRAPHIC_MODEL = "demographicModel";

        public String getParserName() {
            return WITHIN_CASE_COALSECENT;
        }

        public Object parseXMLObject(XMLObject xo) throws XMLParseException {

            TreeModel virusTree = (TreeModel) xo.getChild(TreeModel.class);

            String startingNetworkFileName=null;

            if(xo.hasChildNamed(STARTING_NETWORK)){
                startingNetworkFileName = (String) xo.getElementFirstChild(STARTING_NETWORK);
            }

            AbstractOutbreak caseSet = (AbstractOutbreak) xo.getChild(AbstractOutbreak.class);

            CaseToCaseTreeLikelihood likelihood;

            Parameter infectionTimes = (Parameter) xo.getElementFirstChild(INFECTION_TIMES);

            Parameter infectiousTimes = xo.hasChildNamed(INFECTIOUS_TIMES)
                    ? (Parameter) xo.getElementFirstChild(INFECTIOUS_TIMES) : null;

            Parameter earliestFirstInfection = (Parameter) xo.getElementFirstChild(MAX_FIRST_INF_TO_ROOT);

            DemographicModel demoModel = (DemographicModel) xo.getElementFirstChild(DEMOGRAPHIC_MODEL);

            try {
                likelihood = new WithinCaseCoalescent(virusTree, caseSet, startingNetworkFileName, infectionTimes,
                        infectiousTimes, earliestFirstInfection, demoModel);
            } catch (TaxonList.MissingTaxonException e) {
                throw new XMLParseException(e.toString());
            }

            return likelihood;
        }

        public String getParserDescription() {
            return "This element provides a tree prior for a partitioned tree, with each partitioned tree generated" +
                    "by a coalescent process";
        }

        public Class getReturnType() {
            return WithinCaseCoalescent.class;
        }

        public XMLSyntaxRule[] getSyntaxRules() {
            return rules;
        }

        private final XMLSyntaxRule[] rules = {
                new ElementRule(TreeModel.class, "The tree"),
                new ElementRule(WithinCaseCategoryOutbreak.class, "The set of cases"),
                new ElementRule("startingNetwork", String.class, "A CSV file containing a specified starting network",
                        true),
                new ElementRule(MAX_FIRST_INF_TO_ROOT, Parameter.class, "The maximum time from the first infection to" +
                        "the root node"),
                new ElementRule(INFECTION_TIMES, Parameter.class),
                new ElementRule(INFECTIOUS_TIMES, Parameter.class, "For each case, proportions of the time between " +
                        "infection and first event that requires infectiousness (further infection or cull)" +
                        "that has elapsed before infectiousness", true),
                new ElementRule(DEMOGRAPHIC_MODEL, DemographicModel.class, "The demographic model for within-case" +
                        "evolution")
        };
    };
}
