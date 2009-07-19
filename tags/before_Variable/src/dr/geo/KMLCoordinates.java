package dr.geo;

import dr.xml.*;

import java.util.StringTokenizer;
import java.util.Arrays;
import java.lang.reflect.Array;

import org.jdom.Element;

/**
 * @author Marc A. Suchard
 */

public class KMLCoordinates {

    public static final String COORDINATES = "coordinates";
    public static final String FORMAT = "%7.5f";
    public static final String SEPERATOR = ",";
    public static final String NEWLINE = "\n";

    public KMLCoordinates(double[] x, double[] y) {
        this(x,y,0.0);
    }

    public KMLCoordinates(double[] x, double[] y, double z) {
        this.x = x;
        this.y = y;

        if (x.length != y.length)
            throw new RuntimeException("Cannot create coordinate system with unbalanced entries");

        this.z = new double[x.length];
        Arrays.fill(this.z, z);
    }

    public KMLCoordinates(double[] x, double[] y, double[] z) {

        this.x = x;
        this.y = y;
        this.z = z;

        if (x.length != y.length && x.length != z.length)
            throw new RuntimeException("Cannot create coordinate system with unbalanced entries");

        length = x.length;       
    }

    public Element toXML() {
        Element thisElement = new Element(COORDINATES);
        StringBuffer bf = new StringBuffer();
        bf.append(NEWLINE);
        for(int i=0; i<x.length; i++) {
            bf.append(String.format(FORMAT,x[i])).append(SEPERATOR);
            bf.append(String.format(FORMAT,y[i])).append(SEPERATOR);
            bf.append(String.format(FORMAT,z[i])).append(NEWLINE);
        }
        thisElement.addContent(bf.toString());
        return thisElement;
    }

    public static XMLObjectParser COORDINATESPARSER = new AbstractXMLObjectParser() {

        public String getParserName() {
            return COORDINATES;
        }

        public Object parseXMLObject(XMLObject xo) throws XMLParseException {

            String value = (String) xo.getChild(0);

            StringTokenizer st1 = new StringTokenizer(value, NEWLINE);
            int count = st1.countTokens();

            double[] x = new double[count];
            double[] y = new double[count];
            double[] z = new double[count];

            for (int i = 0; i < count; i++) {
                String line = st1.nextToken();
                StringTokenizer st2 = new StringTokenizer(line, SEPERATOR);
                if (st2.countTokens() != 3)
                    throw new XMLParseException("All KML coordinates must contain (X,Y,Z) values.  Three dimensions not found in element '" + line + "'");
                x[i] = Double.valueOf(st2.nextToken());
                y[i] = Double.valueOf(st2.nextToken());
                z[i] = Double.valueOf(st2.nextToken());
            }

            return new KMLCoordinates(x,y,z);
        }

        //************************************************************************
        // AbstractXMLObjectParser implementation
        //************************************************************************

        public String getParserDescription() {
            return "This element represents a set of (X,Y,Z) coordinates in KML format";
        }

        public Class getReturnType() {
            return KMLCoordinates.class;
        }

        public XMLSyntaxRule[] getSyntaxRules() {
            return rules;
        }

        private XMLSyntaxRule[] rules = new XMLSyntaxRule[]{
//            AttributeRule.newDoubleRule(TIME,true),
                new ElementRule(String.class)
        };
    };

    public double[] x;
    public double[] y;
    public double[] z;

    public int length;

}
