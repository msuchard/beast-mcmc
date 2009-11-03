/*
 * RateCovarianceStatistic.java
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

package dr.evomodel.tree;

import dr.evolution.tree.NodeRef;
import dr.evolution.tree.Tree;
import dr.evomodel.branchratemodel.BranchRateModel;
import dr.inference.model.Statistic;
import dr.stats.DiscreteStatistics;
import dr.xml.*;

/**
 * A statistic that tracks the covariance of rates on branches
 *
 * @author Alexei Drummond
 * @version $Id: RateCovarianceStatistic.java,v 1.5 2005/07/11 14:06:25 rambaut Exp $
 */
public class RateCovarianceStatistic extends Statistic.Abstract implements TreeStatistic {

    public static final String RATE_COVARIANCE_STATISTIC = "rateCovarianceStatistic";

    public RateCovarianceStatistic(String name, Tree tree, BranchRateModel branchRateModel) {
        super(name);
        this.tree = tree;
        this.branchRateModel = branchRateModel;

        int n = tree.getExternalNodeCount();
        childRate = new double[2 * n - 4];
        parentRate = new double[childRate.length];
    }

    public void setTree(Tree tree) {
        this.tree = tree;
    }

    public Tree getTree() {
        return tree;
    }

    public int getDimension() {
        return 1;
    }

    /**
     * @return the height of the MRCA node.
     */
    public double getStatisticValue(int dim) {

        int n = tree.getNodeCount();
        int index = 0;
        for (int i = 0; i < n; i++) {
            NodeRef child = tree.getNode(i);
            NodeRef parent = tree.getParent(child);
            if (parent != null & !tree.isRoot(parent)) {
                childRate[index] = branchRateModel.getBranchRate(tree, child);
                parentRate[index] = branchRateModel.getBranchRate(tree, parent);
                index += 1;
            }
        }
        return DiscreteStatistics.covariance(childRate, parentRate);
    }

    public static XMLObjectParser PARSER = new AbstractXMLObjectParser() {

        public String getParserName() {
            return RATE_COVARIANCE_STATISTIC;
        }

        public Object parseXMLObject(XMLObject xo) throws XMLParseException {

            String name = xo.getAttribute(NAME, xo.getId());
            Tree tree = (Tree) xo.getChild(Tree.class);
            BranchRateModel branchRateModel = (BranchRateModel) xo.getChild(BranchRateModel.class);

            return new RateCovarianceStatistic(name, tree, branchRateModel);
        }

        //************************************************************************
        // AbstractXMLObjectParser implementation
        //************************************************************************

        public String getParserDescription() {
            return "A statistic that has as its value the covariance of parent and child branch rates";
        }

        public Class getReturnType() {
            return RateCovarianceStatistic.class;
        }

        public XMLSyntaxRule[] getSyntaxRules() {
            return rules;
        }

        private final XMLSyntaxRule[] rules = {
                new ElementRule(TreeModel.class),
                new ElementRule(BranchRateModel.class),
                new StringAttributeRule("name", "A name for this statistic primarily for the purposes of logging", true),
        };
    };

    private Tree tree = null;
    private BranchRateModel branchRateModel = null;
    private double[] childRate = null;
    private double[] parentRate = null;
}
