package br.com.critplay.media;

import br.com.critplay.shared.BadRequestException;
import javax.imageio.*;
import javax.imageio.metadata.IIOMetadataNode;
import org.w3c.dom.Node;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

/** Re-encodes decoded GIF frames, retaining only animation/rendering metadata. */
final class GifEncoder {
    private static final String STREAM="javax_imageio_gif_stream_1.0",IMAGE="javax_imageio_gif_image_1.0";
    private GifEncoder() {}
    static void write(ImageReader reader,Path target,Path poster) throws Exception {
        var streamTree=(IIOMetadataNode)reader.getStreamMetadata().getAsTree(STREAM);
        var screen=child(streamTree,"LogicalScreenDescriptor");
        int width=integer(screen,"logicalScreenWidth"),height=integer(screen,"logicalScreenHeight");
        ImageService.validateDimensions(width,height);
        int count=reader.getNumImages(true);
        if(count<1||count>120||(long)width*height*count>60_000_000)
            throw new BadRequestException("GIF muito grande. Use uma animação mais curta ou com resolução menor (máximo de 120 quadros).");
        var writer=ImageIO.getImageWritersByFormatName("gif").next();
        try(var output=ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(output);
            var stream=writer.getDefaultStreamMetadata(writer.getDefaultWriteParam());stream.setFromTree(STREAM,streamTree);
            writer.prepareWriteSequence(stream);
            for(int index=0;index<count;index++) {
                var tree=(IIOMetadataNode)reader.getImageMetadata(index).getAsTree(IMAGE);
                var descriptor=child(tree,"ImageDescriptor");
                int left=integer(descriptor,"imageLeftPosition"),top=integer(descriptor,"imageTopPosition");
                int frameWidth=reader.getWidth(index),frameHeight=reader.getHeight(index);
                if(frameWidth<1||frameHeight<1||left<0||top<0||(long)left+frameWidth>width||(long)top+frameHeight>height)
                    throw new BadRequestException("Os quadros do GIF precisam caber na imagem.");
                var extensions=child(tree,"ApplicationExtensions");byte[] loop=null;
                if(index==0&&extensions!=null)for(Node node=extensions.getFirstChild();node!=null;node=node.getNextSibling()) {
                    var extension=(IIOMetadataNode)node;
                    if("NETSCAPE".equals(extension.getAttribute("applicationID"))&&"2.0".equals(extension.getAttribute("authenticationCode"))
                        &&extension.getUserObject() instanceof byte[] bytes&&bytes.length==3&&bytes[0]==1)loop=bytes.clone();
                }
                for(String name:new String[]{"CommentExtensions","PlainTextExtension","ApplicationExtensions"}) {
                    var node=child(tree,name);if(node!=null)tree.removeChild(node);
                }
                if(loop!=null){var applications=new IIOMetadataNode("ApplicationExtensions");var extension=new IIOMetadataNode("ApplicationExtension");
                    extension.setAttribute("applicationID","NETSCAPE");extension.setAttribute("authenticationCode","2.0");extension.setUserObject(loop);applications.appendChild(extension);tree.appendChild(applications);}
                var image=reader.read(index);
                if(index==0) {
                    var still=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);var graphics=still.createGraphics();
                    try {
                        var control=child(tree,"GraphicControlExtension");var palette=child(streamTree,"GlobalColorTable");
                        if(palette!=null&&(control==null||!"TRUE".equalsIgnoreCase(control.getAttribute("transparentColorFlag")))) {
                            int background=integer(palette,"backgroundColorIndex");
                            for(Node color=palette.getFirstChild();color!=null;color=color.getNextSibling())if(integer(color,"index")==background) {
                                graphics.setColor(new java.awt.Color(integer(color,"red"),integer(color,"green"),integer(color,"blue")));graphics.fillRect(0,0,width,height);break;
                            }
                        }
                        graphics.drawImage(image,left,top,null);
                    } finally {graphics.dispose();}
                    if(!ImageIO.write(still,"png",poster.toFile()))throw new IllegalStateException("PNG writer unavailable");
                }
                var metadata=writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image),writer.getDefaultWriteParam());
                metadata.setFromTree(IMAGE,tree);writer.writeToSequence(new IIOImage(image,null,metadata),writer.getDefaultWriteParam());
            }
            writer.endWriteSequence();
        } finally {writer.dispose();}
    }
    private static IIOMetadataNode child(Node parent,String name){
        for(Node node=parent.getFirstChild();node!=null;node=node.getNextSibling())if(node.getNodeName().equals(name))return (IIOMetadataNode)node;return null;
    }
    private static int integer(Node node,String name){return Integer.parseInt(node.getAttributes().getNamedItem(name).getNodeValue());}
}
