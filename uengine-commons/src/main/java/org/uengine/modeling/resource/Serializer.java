package org.uengine.modeling.resource;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.security.WildcardTypePermission;
import org.uengine.kernel.NeedArrangementToSerialize;

import java.io.*;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Created by jangjinyoung on 15. 7. 12..
 */
public class Serializer {

    public static XStream xstream = new XStream(/*new DomDriver()*/);
    static{
        xstream.ignoreUnknownElements();
        // XStream 1.4+ 보안: 프로세스 변수 역직렬화에 필요한 타입 허용 (IAMCompanyRoleMapping 등)
        xstream.addPermission(new WildcardTypePermission(new String[]{
                "org.uengine.**",
                "java.**",
                "javax.**",
                "jakarta.**"
        }));
        xstream.registerConverter(new IndexedProcessVariableMapConverter());
    }
    public final static String DATABASE_ENCODING = "UTF-8";


    public static void serialize(Object obj, OutputStream os) throws Exception{
        if(obj instanceof NeedArrangementToSerialize)
            ((NeedArrangementToSerialize)obj).beforeSerialization();

        try{
            xstream.toXML(obj, new OutputStreamWriter(os, "UTF-8"));
            //
        }catch(Exception ex){
            throw ex;
        }finally{
            try{os.close();}catch(Exception exx){};
        }
    }

    public static String serialize(Object obj) throws Exception{
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        serialize(obj, bao);
        return bao.toString(DATABASE_ENCODING);
    }

    public static Object deserialize(String src) throws Exception{
        ByteArrayInputStream bis = new ByteArrayInputStream(src.getBytes(DATABASE_ENCODING));

        return deserialize(bis);
    }

    public static Object deserialize(InputStream is) throws Exception{

        Object obj = null;

        try{
            obj = xstream.fromXML(new InputStreamReader(is, "UTF-8"));
        }catch(Exception e){
            throw e;
        }finally{
            try{is.close();}catch(Exception exx){};
        }

        if(obj instanceof NeedArrangementToSerialize)
            ((NeedArrangementToSerialize)obj).afterDeserialization();

        return obj;
    }

    static class IndexedProcessVariableMapConverter implements Converter {

        private static final String TYPE_NAME = "org.uengine.kernel.IndexedProcessVariableMap";

        @Override
        public boolean canConvert(Class type) {
            return TYPE_NAME.equals(type.getName());
        }

        @Override
        public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
            try {
                Method getMaxIndex = source.getClass().getMethod("getMaxIndex");
                writer.addAttribute("maxIndex", String.valueOf(getMaxIndex.invoke(source)));
            } catch (Exception ignored) {
            }

            for (Object entryObject : ((Map<?, ?>) source).entrySet()) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entryObject;
                writer.startNode("entry");
                writer.addAttribute("key", String.valueOf(entry.getKey()));
                writer.startNode("value");
                context.convertAnother(entry.getValue());
                writer.endNode();
                writer.endNode();
            }
        }

        @Override
        public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
            try {
                Object map = Class.forName(TYPE_NAME).getDeclaredConstructor().newInstance();
                String maxIndex = reader.getAttribute("maxIndex");
                if (maxIndex != null) {
                    Method setMaxIndex = map.getClass().getMethod("setMaxIndex", int.class);
                    setMaxIndex.invoke(map, Integer.parseInt(maxIndex));
                }

                while (reader.hasMoreChildren()) {
                    reader.moveDown();
                    String key = reader.getAttribute("key");
                    Object value = null;
                    if (reader.hasMoreChildren()) {
                        reader.moveDown();
                        if (reader.hasMoreChildren()) {
                            value = context.convertAnother(map, Object.class);
                        } else {
                            value = reader.getValue();
                        }
                        reader.moveUp();
                    } else if (reader.getValue() != null) {
                        value = reader.getValue();
                    }
                    ((Map<Object, Object>) map).put(Integer.valueOf(key), value);
                    reader.moveUp();
                }
                return map;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }



}
