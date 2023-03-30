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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FkHandler {

    public static void main(String[] args) {

        try {
            Document doc = loadDocument("src/test/resources/fkTestmap.map.xml");
            Document anotherDoc = loadDocument("src/test/resources/anotherFkTestmap.map.xml");

            Element dataMap = doc.getDocumentElement();
            Element anotherDataMap = anotherDoc.getDocumentElement();

            NodeList relationships = dataMap.getElementsByTagName("db-relationship");

            NodeList dbEntities = dataMap.getElementsByTagName("db-entity");
            NodeList anotherDbEntities = anotherDataMap.getElementsByTagName("db-entity");
            List<NodeList> combinedDbEntityList = Arrays.asList(dbEntities, anotherDbEntities);

            for (int i = 0; i < relationships.getLength(); i++) {
                Node relationship = relationships.item(i);

                Element reverseRelationship = findReverseRelationship(relationships, relationship);

                if (reverseRelationship == null) {
                    reverseRelationship = findReverseInAnotherDatamaps(Collections.singletonList(anotherDataMap), relationship);
                }

                if (reverseRelationship == null && isNotFK(relationship)) {
                    setFk((Element) relationship);
                }

                if (reverseRelationship != null
                        && !isToDepPK(relationship)
                        && !isToDepPK(reverseRelationship)
                        && isNotFK(relationship)
                        && isNotFK(reverseRelationship)) {
                    setFk((Element) relationship, reverseRelationship, combinedDbEntityList);
                }

                if (isToDepPK(relationship)) {
                    handleToDepPK(relationship, reverseRelationship);
                }

            }

            saveUpdatedXML(doc, "modifiedDatamap.xml");
            saveUpdatedXML(anotherDoc, "modifiedAnotherDatamap.xml");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Element findReverseInAnotherDatamaps(List<Element> dataMaps, Node relationship) {
        for (Element dataMap : dataMaps) {
            NodeList relationships = dataMap.getElementsByTagName("db-relationship");
            Element reverseRelationship = findReverseRelationship(relationships, relationship);
            if (reverseRelationship != null) {
                System.out.println("Found revers in another for: " + relationship.getAttributes().getNamedItem("name"));
                return reverseRelationship;
            }
        }
        System.out.println("Not found in another for: " + relationship.getAttributes().getNamedItem("name"));
        return null;
    }


    private static void setFk(Element relationship) {
        System.out.println("Reverse not found for: " + relationship.getAttribute("name"));
    }

    private static void setFk(Element relationship, Element reverseRelationship, List<NodeList> combinedDbEntityList) {

        NodeList pairNodes = relationship.getElementsByTagName("db-attribute-pair");
        for (int i = 0; i < pairNodes.getLength(); i++) {
            String joinSource = pairNodes.item(i).getAttributes().getNamedItem("source").getNodeValue();
            String joinTarget = pairNodes.item(i).getAttributes().getNamedItem("target").getNodeValue();

            NamedNodeMap relationshipAttrs = relationship.getAttributes();

            String sourceEntityName = relationshipAttrs.getNamedItem("source").getNodeValue();
            String targetEntityName = relationshipAttrs.getNamedItem("target").getNodeValue();

            Node sourceEntity = getDbEntityByName(combinedDbEntityList, sourceEntityName);
            Node targetEntity = getDbEntityByName(combinedDbEntityList, targetEntityName);

            if (sourceEntity != null && targetEntity != null) {
                NodeList sourceChildNodes = sourceEntity.getChildNodes();
                NodeList targetChildNodes = targetEntity.getChildNodes();

                boolean sourceIsPrimaryKey = isPrimaryKey(joinSource, sourceChildNodes);
                boolean targetIsPrimaryKey = isPrimaryKey(joinTarget, targetChildNodes);

                //TODO case if both PK or NPK
                if (sourceIsPrimaryKey != targetIsPrimaryKey) {
                    if (sourceIsPrimaryKey) {
                        reverseRelationship.setAttribute("fk", "true");
                        System.out.printf("Set fk=true in %s %n", reverseRelationship.getAttribute("name"));
                    } else {
                        relationship.setAttribute("fk", "true");
                        System.out.printf("Set fk=true in %s %n", relationship.getAttribute("name"));
                    }
                    return;
                }
            }
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

    private static Node getDbEntityByName(List<NodeList> combinedDbEntityList, String searchedEntityName) {
        for (NodeList list : combinedDbEntityList) {
            for (int i = 0; i < list.getLength(); i++) {
                String entityName = list.item(i).getAttributes().getNamedItem("name").getNodeValue();
                if (searchedEntityName.equals(entityName)) {
                    return list.item(i);
                }
            }
        }
        return null;
    }

    private static boolean isToDepPK(Node relationship) {
        NamedNodeMap relationshipAttrs = relationship.getAttributes();
        Node toDependentPK = relationshipAttrs.getNamedItem("toDependentPK");
        return toDependentPK != null && toDependentPK.getNodeValue().equalsIgnoreCase("true");
    }

    private static boolean isNotFK(Node relationship) {
        NamedNodeMap relationshipAttrs = relationship.getAttributes();
        Node toDependentPK = relationshipAttrs.getNamedItem("fk");
        return toDependentPK == null || !toDependentPK.getNodeValue().equalsIgnoreCase("true");
    }

    private static void handleToDepPK(Node relationship, Element reverseRelationship) {
        NamedNodeMap relationshipAttrs = relationship.getAttributes();
        if (reverseRelationship != null) {
            reverseRelationship.setAttribute("fk", "true");
            relationshipAttrs.removeNamedItem("toDependentPK");
            System.out.println(relationship.getAttributes().getNamedItem("name") + " handled toDepPk");
        }
    }

    private static Element findReverseRelationship(NodeList dbRelationshipList, Node dbRelationshipNode) {
        Element dbRelationship = (Element) dbRelationshipNode;

        String sourceAttr = dbRelationship.getAttribute("source");
        String targetAttr = dbRelationship.getAttribute("target");

        List<DbAttrPair> pairs = getDbAttrPairs(dbRelationship);

        for (int j = 0; j < dbRelationshipList.getLength(); j++) {
            Node candidateDbRelationshipNode = dbRelationshipList.item(j);
            if (candidateDbRelationshipNode.getNodeType() == Node.ELEMENT_NODE) {
                Element candidateDbRelationship = (Element) candidateDbRelationshipNode;

                String candidateSourceAttr = candidateDbRelationship.getAttribute("source");
                String candidateTargetAttr = candidateDbRelationship.getAttribute("target");

                List<DbAttrPair> candidatePairs = getDbAttrPairs(candidateDbRelationship);

                if (sourceAttr.equals(candidateTargetAttr)
                        && targetAttr.equals(candidateSourceAttr)
                        && pairs.size() == candidatePairs.size()
                        && containAllReversed(pairs, candidatePairs)) {

                    System.out.println("Found reverse for " + dbRelationship.getAttribute("name") + ": " + candidateDbRelationship.getAttribute("name"));
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

    private static List<DbAttrPair> getDbAttrPairs(Element dbRelationship) {
        List<DbAttrPair> pairs = new ArrayList<>();
        NodeList attributes = dbRelationship.getElementsByTagName("db-attribute-pair");
        for (int j = 0; j < attributes.getLength(); j++) {
            Node attributeNode = attributes.item(j);
            if (attributeNode.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) attributeNode;
                String pairSource = element.getAttribute("source");
                String pairTarget = element.getAttribute("target");
                pairs.add(new DbAttrPair(pairSource, pairTarget));
            }
        }
        return pairs;
    }

    private static Document loadDocument(String path) throws ParserConfigurationException, SAXException, IOException {
        File inputFile = new File(path);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(inputFile);
        doc.getDocumentElement().normalize();
        return doc;
    }

    private static void saveUpdatedXML(Document doc, String path) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(new File(path));
        transformer.transform(source, result);
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




