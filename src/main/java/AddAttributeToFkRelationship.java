import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AddAttributeToFkRelationship {

    public static void main(String[] args) {


        try {
            //  Document doc = loadDocument("simpleTestmap.map.xml");
           // Document doc = loadDocument("testmap.map.xml");
            Document doc = loadDocument("anotherTestmap.map.xml");
            //   Document doc = loadDocument("Exampletestmap.map.xml");
            Element dataMap = doc.getDocumentElement();

            NodeList relationships = dataMap.getElementsByTagName("db-relationship");
            NodeList dbEntities = dataMap.getElementsByTagName("db-entity");

            for (int i = 0; i < relationships.getLength(); i++) {
                Node relationship = relationships.item(i);

                Element reverseRelationship = findReverseRelationship(relationships, relationship);

                if (reverseRelationship == null) {
                    setFk((Element) relationship);
                }

                if (reverseRelationship != null && !isToDepPK(relationship) && !isToDepPK(reverseRelationship)) {
                    setFk((Element) relationship, reverseRelationship, dbEntities);
                }

                if (isToDepPK(relationship)) {
                    handleToDepPK(relationship, reverseRelationship);
                }

            }

            saveUpdatedXML(doc, "output.xml");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static void setFk(Element relationship) {
        System.out.println("Reverse not found for: " + relationship.getAttribute("name"));
    }

    private static void setFk(Element relationship, Element reverseRelationship, NodeList dbEntities) {
        NamedNodeMap relationshipAttrs = relationship.getAttributes();
        NamedNodeMap revRelationshipAttrs = reverseRelationship.getAttributes();

        NamedNodeMap dbJoinAttrs = relationship.getFirstChild().getNextSibling().getAttributes();
        String joinSource = dbJoinAttrs.getNamedItem("source").getNodeValue();
        String joinTarget = dbJoinAttrs.getNamedItem("target").getNodeValue();

        String sourceEntityName = relationshipAttrs.getNamedItem("source").getNodeValue();
        String targetEntityName = relationshipAttrs.getNamedItem("target").getNodeValue();

        Node sourceEntity = getDbEntityByName(dbEntities, sourceEntityName);
        Node targetEntity = getDbEntityByName(dbEntities, targetEntityName);

        if (sourceEntity != null && targetEntity != null) {
            NodeList sourceChildNodes = sourceEntity.getChildNodes();
            NodeList targetChildNodes = targetEntity.getChildNodes();

            boolean sourceIsPrimaryKey = isPrimaryKey(joinSource, sourceChildNodes);
            boolean targetIsPrimaryKey = isPrimaryKey(joinTarget, targetChildNodes);

            //TODO case if both PK or NPK
            if (sourceIsPrimaryKey != targetIsPrimaryKey) {
                if (sourceIsPrimaryKey) {
                    reverseRelationship.setAttribute("fk", "true");
                } else {
                    relationship.setAttribute("fk", "true");
                }
            }

            System.out.printf("F %s %s %s : %b%n", relationshipAttrs.getNamedItem("name"), relationshipAttrs.getNamedItem("source"), relationshipAttrs.getNamedItem("target"), sourceIsPrimaryKey);
            System.out.printf("R %s %s %s : %b%n", revRelationshipAttrs.getNamedItem("name"), revRelationshipAttrs.getNamedItem("source"), revRelationshipAttrs.getNamedItem("target"), targetIsPrimaryKey);
        }
    }

    private static boolean isPrimaryKey(String joinName, NodeList childNodes) {
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node item = childNodes.item(i);
            if (item.getAttributes() != null) {
                String name = item.getAttributes().getNamedItem("name").getNodeValue();
                if (name.equals(joinName)) {
                    Node isPrimaryKey = item.getAttributes().getNamedItem("isPrimaryKey");
                    if (isPrimaryKey != null) {
                        return isPrimaryKey.getNodeValue().equals("true");
                    }
                }
            }
        }
        return false;
    }

    private static Node getDbEntityByName(NodeList dbEntities, String searchedEntityName) {
        for (int i = 0; i < dbEntities.getLength(); i++) {
            String entityName = dbEntities.item(i).getAttributes().getNamedItem("name").getNodeValue();
            if (searchedEntityName.equals(entityName)) {
                return dbEntities.item(i);
            }
        }
        return null;
    }

    private static boolean isToDepPK(Node relationship) {
        NamedNodeMap relationshipAttrs = relationship.getAttributes();
        Node toDependentPK = relationshipAttrs.getNamedItem("toDependentPK");
        return toDependentPK != null && toDependentPK.getNodeValue().equalsIgnoreCase("true");
    }

    private static void saveUpdatedXML(Document doc, String path) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(path));
        transformer.transform(source, result);
    }

    private static void handleToDepPK(Node relationship, Element reverseRelationship) {
        NamedNodeMap relationshipAttrs = relationship.getAttributes();
        if (reverseRelationship != null) {
            reverseRelationship.setAttribute("fk", "true");
            relationshipAttrs.removeNamedItem("toDependentPK");
        }
    }

    private static Document loadDocument(String path) throws ParserConfigurationException, SAXException, IOException {
        File inputFile = new File(path);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(inputFile);
        doc.getDocumentElement().normalize();
        return doc;
    }

    private static Element findReverseRelationship(NodeList dbRelationshipList, Node dbRelationshipNode) {
        Element dbRelationship = (Element) dbRelationshipNode;

        String source = dbRelationship.getAttribute("source");
        String target = dbRelationship.getAttribute("target");

        List<DbAttrPair> pairs = getDbAttrPairs(dbRelationship);

        for (int j = 0; j < dbRelationshipList.getLength(); j++) {
            Node candidateDbRelationshipNode = dbRelationshipList.item(j);
            if (candidateDbRelationshipNode.getNodeType() == Node.ELEMENT_NODE) {
                Element candidateDbRelationship = (Element) candidateDbRelationshipNode;

                String candidateSource = candidateDbRelationship.getAttribute("source");
                String candidateTarget = candidateDbRelationship.getAttribute("target");

                List<DbAttrPair> candidatePairs = getDbAttrPairs(candidateDbRelationship);

                if (source.equals(candidateTarget)
                        && target.equals(candidateSource)
                        && pairs.size() == candidatePairs.size()
                        && containAllReversed(pairs,candidatePairs)) {

                    System.out.println("Found reverse for "+dbRelationship.getAttribute("name")+": " +candidateDbRelationship.getAttribute("name") );
                    return candidateDbRelationship;
                }
            }
        }
        return null;
    }

    private static boolean containAllReversed(List<DbAttrPair> pairs, List<DbAttrPair> candidatePairs) {
        return pairs.stream()
                .allMatch(pair -> candidatePairs.stream().anyMatch(pair::isReverseFor));
    }

//    private static boolean containAllReversed(List<SourceTargetPair> pairs, List<SourceTargetPair> candidatePairs) {
//        for (SourceTargetPair pair : pairs) {
//            boolean reversePairFound = false;
//            for (SourceTargetPair candidatePair : candidatePairs) {
//                if (pair.isReverse(candidatePair)) {
//                    reversePairFound = true;
//                    break;
//                }
//            }
//            if (!reversePairFound) {
//                return false;
//            }
//        }
//        return true;
//    }



    private static List<DbAttrPair> getDbAttrPairs(Element dbRelationship){
        List<DbAttrPair> pairs = new ArrayList<>();

        NodeList attributes = dbRelationship.getElementsByTagName("db-attribute-pair");
        for (int j = 0; j < attributes.getLength(); j++) {
            Node attributeNode = attributes.item(j);
            if (attributeNode.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) attributeNode;
                String pairSource = element.getAttribute("source");
                String pairTarget = element.getAttribute("target");
                pairs.add(new DbAttrPair(pairSource,pairTarget));
            }
        }
        return pairs;
    }


    private static class DbAttrPair {
        private final String source;
        private final String target;
        public DbAttrPair(String source, String target) {
            this.source = source;
            this.target = target;
        }

        private boolean isReverseFor(DbAttrPair pair) {
            return this.source.equals(pair.target) && this.target.equals(pair.source);
        }

    }
}




