import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class ReverseRelationshipFinderOld {

  public static void main(String[] args) {
    try {
      // Load the XML file
      File file = new File("Exampletestmap.map.xml");
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      Document doc = factory.newDocumentBuilder().parse(file);
      doc.getDocumentElement().normalize();

      // Find all db-relationship elements
      NodeList relationshipNodes = doc.getElementsByTagName("db-relationship");

      // Iterate over each db-relationship element
      for (int i = 0; i < relationshipNodes.getLength(); i++) {
        Element relationshipElement = (Element) relationshipNodes.item(i);

        // Get the name, source, and target attributes
        String name = relationshipElement.getAttribute("name");
        String source = relationshipElement.getAttribute("source");
        String target = relationshipElement.getAttribute("target");

        // Find all db-attribute-pair elements for this relationship
        NodeList attributePairNodes = relationshipElement.getElementsByTagName("db-attribute-pair");

        // Iterate over each db-attribute-pair element
        List<String> sources = new ArrayList<String>();
        List<String> targets = new ArrayList<String>();
        for (int j = 0; j < attributePairNodes.getLength(); j++) {
          Element attributePairElement = (Element) attributePairNodes.item(j);

          // Get the source and target attributes
          String attributeSource = attributePairElement.getAttribute("source");
          String attributeTarget = attributePairElement.getAttribute("target");

          // Add them to our lists
          sources.add(attributeSource);
          targets.add(attributeTarget);
        }

        // Now check if there is a reverse relationship with the same number of attribute pairs
        NodeList allRelationshipNodes = doc.getElementsByTagName("db-relationship");
        for (int j = 0; j < allRelationshipNodes.getLength(); j++) {
          Element otherRelationshipElement = (Element) allRelationshipNodes.item(j);

          // Skip the same relationship
          if (relationshipElement.equals(otherRelationshipElement)) {
            continue;
          }

          // Get the other relationship's source and target attributes
          String otherSource = otherRelationshipElement.getAttribute("source");
          String otherTarget = otherRelationshipElement.getAttribute("target");

          // Check if they match our target and source attributes respectively
          if (source.equals(otherTarget) && target.equals(otherSource)) {

            // Find all db-attribute-pair elements for this other relationship
            NodeList otherAttributePairNodes = otherRelationshipElement.getElementsByTagName("db-attribute-pair");

            // If the number of attribute pairs matches, check if they have the same values but reversed
            if (attributePairNodes.getLength() == otherAttributePairNodes.getLength()) {
              boolean isReverseRelationship = true;
              for (int k = 0; k < attributePairNodes.getLength(); k++) {
                Element attributePairElement = (Element) attributePairNodes.item(k);
                Element otherAttributePairElement = (Element) otherAttributePairNodes.item(k);

                String attributeSource = attributePairElement.getAttribute("source");
                String attributeTarget = attributePairElement.getAttribute("target");
                String otherAttributeSource = otherAttributePairElement.getAttribute("source");
                String otherAttributeTarget = otherAttributePairElement.getAttribute("target");

                if (!attributeSource.equals(otherAttributeTarget) || !attributeTarget.equals(otherAttributeSource)) {
                  isReverseRelationship = false;
                  break;
                }
              }

              if (isReverseRelationship) {

                System.out.println("Reverse relationship found for " + name);
              }
            }
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}

