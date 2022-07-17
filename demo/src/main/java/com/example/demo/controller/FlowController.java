package com.example.demo.controller;

import com.example.demo.model.ScientificWork;
import com.example.demo.model.generatedFreeFormatter.CrisResults;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.forms.xfa.XfaForm;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.StampingProperties;
import org.apache.xerces.dom.DeferredElementImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/flow")
public class FlowController {

    // INPUT
    public static final String AUTHORS_XML = "demo/src/main/resources/author2917.xml";
    public static final String OBRAZAC_PDF_ORIGINAL = "obrazac.pdf";

    // OUTPUT
    public static final String EXTRACTED_NODES_XML = "demo/src/main/resources/extracted_nodes.xml";
    public static final String FILLED_EXTRACTED_NODES_XML = "demo/src/main/resources/filled_extracted_nodes.xml";
    public static final String FILLED_OBRAZAC_PDF = "demo/src/main/resources/filled_obrazac.pdf";

    @GetMapping("/test1")
    public ResponseEntity flowTest1() throws Exception {
        CrisResults cr = unmarshalXML();

        List<ScientificWork> scientificWorks = mapCrisResultsToScientificWorksList(cr).stream().sorted(Comparator.comparing(ScientificWork::getCategory)).collect(Collectors.toList());

        extractNodesFromXfaPDF();

        Document filledDocument = readAndFillExtractedNodesFile(scientificWorks);

        writeFilledNodesToFile(filledDocument);

        fillXfa();

        return new ResponseEntity(HttpStatus.CREATED);
    }

    public CrisResults unmarshalXML() throws JAXBException {
        JAXBContext jc = JAXBContext.newInstance(CrisResults.class);

        Unmarshaller unmarshaller = jc.createUnmarshaller();
        File xml = new File(AUTHORS_XML);
        return (CrisResults) unmarshaller.unmarshal(xml);
    }

    public List<ScientificWork> mapCrisResultsToScientificWorksList(CrisResults cr) {
        List<ScientificWork> scientificWorks = new ArrayList<>();

        for(Object work : cr.getRuleBookOrApvntOrTheses()) {
            if(work instanceof CrisResults.PaperJournal) {
                scientificWorks.add(new ScientificWork(((CrisResults.PaperJournal) work).getEvaluation().getType(), ((CrisResults.PaperJournal) work).getTitle()));
            } else if(work instanceof CrisResults.PaperConference) {
                scientificWorks.add(new ScientificWork(((CrisResults.PaperConference) work).getEvaluation().getType(), ((CrisResults.PaperConference) work).getTitle()));
            } else if(work instanceof CrisResults.Monograph) {
                scientificWorks.add(new ScientificWork(((CrisResults.Monograph) work).getEvaluation().getType(), ((CrisResults.Monograph) work).getTitle()));
            } else if(work instanceof CrisResults.Theses) {
                scientificWorks.add(new ScientificWork(((CrisResults.Theses) work).getEvaluation().getType(), ((CrisResults.Theses) work).getTitle()));
            }
        }

        return scientificWorks;
    }

    public void extractNodesFromXfaPDF() throws Exception {
        PdfDocument pdfDoc = new PdfDocument(new PdfReader(OBRAZAC_PDF_ORIGINAL));
        PdfAcroForm form = PdfAcroForm.getAcroForm(pdfDoc, true);
        XfaForm xfa = form.getXfaForm();

        // Get XFA data under datasets/data.
        Node node = xfa.getDatasetsNode();
        NodeList list = node.getChildNodes();
        for (int i = 0; i < list.getLength(); i++) {
            if ("data".equals(list.item(i).getLocalName())) {
                node = list.item(i);
                break;
            }
        }

        try (FileOutputStream os = new FileOutputStream(EXTRACTED_NODES_XML)) {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(node), new StreamResult(os));
        }

        pdfDoc.close();
    }

    public Document readAndFillExtractedNodesFile(List<ScientificWork> scientificWorks) throws Exception {
        DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc = builder.parse(new File(EXTRACTED_NODES_XML));
        doc.getDocumentElement().normalize();
        var pubMoviNodeList = doc.getElementsByTagName("pubMovi");

        for(ScientificWork work : scientificWorks) {
            var editedNode = pubMoviNodeList.item(Integer.parseInt(work.getCategory().substring(1, work.getCategory().length() - 1)) - 1);

            var numberOfWorksNode = ((DeferredElementImpl) editedNode).getElementsByTagName("pubMBr").item(0);
            var numberOfWorksInteger = Integer.parseInt(numberOfWorksNode.getTextContent().substring(1, numberOfWorksNode.getTextContent().length() - 1));

            if(numberOfWorksInteger == 0) {
                removeRedundantNodes(editedNode);
            }

            numberOfWorksNode.setTextContent("(" + String.valueOf(numberOfWorksInteger + 1) + ")");

            Element pubRbElement = doc.createElement("pubRb");
            pubRbElement.setTextContent(String.valueOf(numberOfWorksInteger + 1) + ".");
            editedNode.appendChild(pubRbElement);

            Element pubNasElement = doc.createElement("pubNas");
            pubNasElement.setTextContent(work.getTitle());
            editedNode.appendChild(pubNasElement);

            Element pubKatElement = doc.createElement("pubKat");
            pubKatElement.setTextContent(work.getCategory());
            editedNode.appendChild(pubKatElement);
        }

        return doc;
    }

    public void writeFilledNodesToFile(Document doc) throws Exception {
        try (FileOutputStream os = new FileOutputStream(FILLED_EXTRACTED_NODES_XML)) {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(doc), new StreamResult(os));
        }
    }

    public void removeRedundantNodes(Node editedNode) {
        var pubRbNode = ((DeferredElementImpl) editedNode).getElementsByTagName("pubRb").item(0);
        var pubNasNode = ((DeferredElementImpl) editedNode).getElementsByTagName("pubNas").item(0);
        var pubKatNode = ((DeferredElementImpl) editedNode).getElementsByTagName("pubKat").item(0);

        editedNode.removeChild(pubRbNode);
        editedNode.removeChild(pubNasNode);
        editedNode.removeChild(pubKatNode);
    }

    public void fillXfa() throws Exception {
        StampingProperties stampingProperties = new StampingProperties();
        stampingProperties.useAppendMode();

        PdfDocument pdfdoc = new PdfDocument(new PdfReader(OBRAZAC_PDF_ORIGINAL), new PdfWriter(FILLED_OBRAZAC_PDF), stampingProperties);
        PdfAcroForm form = PdfAcroForm.getAcroForm(pdfdoc, true);

        XfaForm xfa = form.getXfaForm();

        xfa.fillXfaForm(new FileInputStream(FILLED_EXTRACTED_NODES_XML));
        xfa.write(pdfdoc);

        pdfdoc.close();
    }

}
