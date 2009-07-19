/*
 * LocalClockModel.java
 *
 * Copyright (C) 2002-2006 Alexei Drummond and Andrew Rambaut
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 *  BEAST is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package dr.evomodel.branchratemodel;

import dr.evolution.tree.NodeRef;
import dr.evolution.tree.Tree;
import dr.evolution.util.Taxa;
import dr.evolution.util.TaxonList;
import dr.evolution.util.Taxon;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.AbstractModel;
import dr.inference.model.Model;
import dr.inference.model.Parameter;
import dr.xml.*;

import java.util.*;

/**
 * @author Andrew Rambaut
 * @version $Id: LocalClockModel.java,v 1.1 2005/04/05 09:27:48 rambaut Exp $
 */
public class LocalClockModel extends AbstractModel implements BranchRateModel {

    public static final String LOCAL_CLOCK_MODEL = "localClockModel";
    public static final String RATE = "rate";
    public static final String RELATIVE = "relative";
    public static final String CLADE = "clade";
    public static final String INCLUDE_STEM = "includeStem";
    public static final String EXTERNAL_BRANCHES = "externalBranches";

    private TreeModel treeModel;
    protected Map<Integer, LocalClock> localTipClocks = new HashMap<Integer, LocalClock>();
    protected Map<BitSet, LocalClock> localCladeClocks = new HashMap<BitSet, LocalClock>();
    private boolean updateNodeClocks = true;
    private Map<NodeRef, LocalClock> nodeClockMap = new HashMap<NodeRef, LocalClock>();
    private final Parameter globalRateParameter;

    public LocalClockModel(TreeModel treeModel, Parameter globalRateParameter) {

        super(LOCAL_CLOCK_MODEL);
        this.treeModel = treeModel;

        addModel(treeModel);

        this.globalRateParameter = globalRateParameter;
        addParameter(globalRateParameter);
    }

    private void addExternalBranchClock(TaxonList taxonList, Parameter rateParameter, boolean relative) throws Tree.MissingTaxonException {
        BitSet tips = getTipsForTaxa(treeModel, taxonList);
        LocalClock clock = new LocalClock(rateParameter, relative, tips);
        for (int i = tips.nextSetBit(0); i >= 0; i = tips.nextSetBit(i + 1)) {
            localTipClocks.put(i, clock);
        }
        addParameter(rateParameter); 
    }

    private void addCladeClock(TaxonList taxonList, Parameter rateParameter, boolean relative, boolean includeStem) throws Tree.MissingTaxonException {
        BitSet tips = getTipsForTaxa(treeModel, taxonList);
        LocalClock clock = new LocalClock(rateParameter, relative, tips, includeStem);
        localCladeClocks.put(tips, clock);
        addParameter(rateParameter);
    }

    /**
     * @param tree the tree
     * @param taxa the taxa
     * @return A bitset with the node numbers set.
     * @throws dr.evolution.tree.Tree.MissingTaxonException
     *          if a taxon in taxa is not contained in the tree
     */
    private BitSet getTipsForTaxa(Tree tree, TaxonList taxa) throws Tree.MissingTaxonException {

        BitSet tips = new BitSet();

        for (int i = 0; i < taxa.getTaxonCount(); i++) {

            Taxon taxon = taxa.getTaxon(i);
            boolean found = false;
            for (int j = 0; j < tree.getExternalNodeCount(); j++) {

                NodeRef node = tree.getExternalNode(j);
                if (tree.getNodeTaxon(node).getId().equals(taxon.getId())) {
                    tips.set(node.getNumber());
                    found = true;
                    break;
                }
            }

            if (!found) {
                throw new Tree.MissingTaxonException(taxon);
            }
        }

        return tips;
    }

    public void handleModelChangedEvent(Model model, Object object, int index) {
        updateNodeClocks = true;
    }

    protected final void handleParameterChangedEvent(Parameter parameter, int index, Parameter.ChangeType type) {
        fireModelChanged();
    }

    protected void storeState() {
    }

    protected void restoreState() {
        updateNodeClocks = true;
    }

    protected void acceptState() {
    }


    // BranchRateModel implementation

    public double getBranchRate(Tree tree, NodeRef node) {

        if (tree.isRoot(node)) {
            throw new IllegalArgumentException("root node doesn't have a rate!");
        }

        if (updateNodeClocks) {
            nodeClockMap.clear();
            setupRateParameters(tree, tree.getRoot(), new BitSet());

            updateNodeClocks = false;
        }

        double rate = globalRateParameter.getParameterValue(0);

        LocalClock localClock = nodeClockMap.get(node);
        if (localClock != null) {
            if (localClock.isRelativeRate()) {
                rate *= localClock.getRateParameter().getParameterValue(0);
            } else {
                rate = localClock.getRateParameter().getParameterValue(0);
            }
        }

        return rate;
    }

    public String getBranchAttributeLabel() {
        return "rate";
    }

    public String getAttributeForBranch(Tree tree, NodeRef node) {
        return Double.toString(getBranchRate(tree, node));
    }

    private void setupRateParameters(Tree tree, NodeRef node, BitSet tips) {
        LocalClock clock = null;

        if (tree.isExternal(node)) {
            tips.set(node.getNumber());
            clock = localTipClocks.get(node.getNumber());
        } else {
            for (int i = 0; i < tree.getChildCount(node); i++) {
                NodeRef child = tree.getChild(node, i);
                BitSet childTips = new BitSet();
                setupRateParameters(tree, child, childTips);

                tips.or(childTips);
            }
            clock = localCladeClocks.get(tips);
        }

        if (clock != null) {
            setNodeClock(tree, node, clock, clock.includeStem());
        }
    }

    private void setNodeClock(Tree tree, NodeRef node, LocalClock localClock, boolean includeStem) {

        if (!tree.isExternal(node)) {
            for (int i = 0; i < tree.getChildCount(node); i++) {
                NodeRef child = tree.getChild(node, i);
                setNodeClock(tree, child, localClock, true);
            }
        }

        if (includeStem && !nodeClockMap.containsKey(node)) {
            nodeClockMap.put(node, localClock);
        }
    }

    public static XMLObjectParser PARSER = new AbstractXMLObjectParser() {

        public String getParserName() {
            return LOCAL_CLOCK_MODEL;
        }

        public Object parseXMLObject(XMLObject xo) throws XMLParseException {

            TreeModel tree = (TreeModel) xo.getChild(TreeModel.class);

            Parameter globalRateParameter = (Parameter) xo.getElementFirstChild(RATE);
            LocalClockModel localClockModel = new LocalClockModel(tree, globalRateParameter);

            for (int i = 0; i < xo.getChildCount(); i++) {
                if (xo.getChild(i) instanceof XMLObject) {

                    XMLObject xoc = (XMLObject) xo.getChild(i);
                    if (xoc.getName().equals(CLADE)) {

                        boolean relative  = xoc.getAttribute(RELATIVE, false);

                        Parameter rateParameter = (Parameter) xoc.getChild(Parameter.class);
                        TaxonList taxonList = (TaxonList) xoc.getChild(TaxonList.class);

                        if (taxonList.getTaxonCount() == 1) {
                            throw new XMLParseException("A local clock for a clade must be defined by at least two taxa");
                        }

                        boolean includeStem = false;

                        if (xoc.hasAttribute(INCLUDE_STEM)) {
                            includeStem = xoc.getBooleanAttribute(INCLUDE_STEM);
                        }

                        try {
                            localClockModel.addCladeClock(taxonList, rateParameter, relative, includeStem);

                        } catch (Tree.MissingTaxonException mte) {
                            throw new XMLParseException("Taxon, " + mte + ", in " + getParserName() + " was not found in the tree.");
                        }
                    } else if (xoc.getName().equals(EXTERNAL_BRANCHES)) {

                        boolean relative  = xoc.getAttribute(RELATIVE, false);

                        Parameter rateParameter = (Parameter) xoc.getChild(Parameter.class);
                        TaxonList taxonList = (TaxonList) xoc.getChild(TaxonList.class);


                        try {
                            localClockModel.addExternalBranchClock(taxonList, rateParameter, relative);

                        } catch (Tree.MissingTaxonException mte) {
                            throw new XMLParseException("Taxon, " + mte + ", in " + getParserName() + " was not found in the tree.");
                        }
                    }

                }
            }

            System.out.println("Using local clock branch rate model.");

            return localClockModel;
        }

        //************************************************************************
        // AbstractXMLObjectParser implementation
        //************************************************************************

        public String getParserDescription() {
            return
                    "This element returns a branch rate model that adds a delta to each terminal branch length.";
        }

        public Class getReturnType() {
            return LocalClockModel.class;
        }

        public XMLSyntaxRule[] getSyntaxRules() {
            return rules;
        }

        private XMLSyntaxRule[] rules = new XMLSyntaxRule[]{
                new ElementRule(TreeModel.class),
                new ElementRule(RATE, Parameter.class, "The molecular evolutionary rate parameter", false),
                new ElementRule(EXTERNAL_BRANCHES,
                        new XMLSyntaxRule[]{
                                AttributeRule.newBooleanRule(RELATIVE, true),
                                new ElementRule(Taxa.class, "A local clock that will be applied only to the external branches for these taxa"),
                                new ElementRule(Parameter.class, "The rate parameter"),
                        }, 0, Integer.MAX_VALUE),
                new ElementRule(CLADE,
                        new XMLSyntaxRule[]{
                                AttributeRule.newBooleanRule(RELATIVE, true),
                                AttributeRule.newBooleanRule(INCLUDE_STEM, true, "determines whether or not the stem branch above this clade is included in the siteModel."),
                                new ElementRule(Taxa.class, "A set of taxa which defines a clade to apply a different site model to"),
                                new ElementRule(Parameter.class, "The rate parameter"),
                        }, 0, Integer.MAX_VALUE)
        };
    };

    private class LocalClock {
        LocalClock(Parameter rateParameter, boolean relativeRate, BitSet tips) {
            this.rateParameter = rateParameter;
            this.relativeRate = relativeRate;
            this.tips = tips;
            this.isClade = false;
            this.includeStem = true;
        }

        LocalClock(Parameter rateParameter, boolean relativeRate, BitSet tips, boolean includeStem) {
            this.rateParameter = rateParameter;
            this.relativeRate = relativeRate;
            this.tips = tips;
            this.isClade = true;
            this.includeStem = includeStem;
        }

        boolean includeStem() {
            return this.includeStem;
        }

        boolean isClade() {
            return this.isClade;
        }

        public boolean isRelativeRate() {
            return relativeRate;
        }

        Parameter getRateParameter() {
            return this.rateParameter;
        }

        private final Parameter rateParameter;
        private final boolean relativeRate;
        private final BitSet tips;
        private final boolean isClade;
        private final boolean includeStem;
    }

}