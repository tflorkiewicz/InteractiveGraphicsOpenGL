/* class Light
 * Description of a light source
 *
 * Doug DeCarlo
 */
import java.io.*;
import java.text.ParseException;
import java.lang.reflect.*;
import javax.vecmath.*;

class Light extends RaytracerObject
{
    final public static String keyword = "light";
    
    /** parameters of the light source */

    // Location of light (one of these is always null), depending on
    // whether the light is directional light or not
    Point3d  position    = new Point3d();
    Vector3d direction   = null;

    // Light color (R, G, B)
    Vector3d color       = new Vector3d(1.0, 1.0, 1.0);

    // Light attenuation (Kc, Kl, Kq); default has no attenuation
    //    - given distance D from a light (not directional)
    //    - atten factor is 1/(Kc + Kl * D + Kq * D^2)
    //    - ambient light is not attenuated
    Vector3d attenuation = new Vector3d(1.0, 0.0, 0.0);

    //------------------------------------------------------------------------

    /** constructor that reads the content of the object from the tokenizer */
    public Light(StreamTokenizer tokenizer)
        throws ParseException, IOException, NoSuchMethodException,
        ClassNotFoundException,IllegalAccessException,
        InvocationTargetException
    {
        super(tokenizer);

        // add the parameters
        addSpec("position",     "setPosition",
                position.getClass().getName());
        addSpec("direction",    "setDirection", 
                (new Vector3d()).getClass().getName());
        addSpec("color",        "setColor",
                color.getClass().getName());
        addSpec("attenuation",  "setAttenuation",
                attenuation.getClass().getName());

        // read the content of this object
        read(tokenizer);
    }

    /** transform light location given matrix m */
    public void transform(Matrix4d m)
    {
        if (isDirectional()) {
            m.transform(direction);
        } else {
            m.transform(position);
        }
    }

    //------------------------------------------------------------------------

    // accessors
    public Point3d  getPosition()    { return position;    }
    public Vector3d getDirection()   { return direction;   }
    public Vector3d getColor()       { return color;       }
    public Vector3d getAttenuation() { return attenuation; }

    public void setPosition(Point3d p)
    {
        if (position != null)
          position.set(p);
        else
          position = new Point3d(p);

        direction = null;
    }

    public void setDirection(Vector3d d)
    {
        if (direction != null)
          direction.set(d);
        else
          direction = new Vector3d(d);
	
        // Direction is always normalized
        direction.normalize();
	
        position = null;
    }

    public void setColor(Vector3d c)        { color = c; }
    public void setAttenuation (Vector3d a) { attenuation = a; }

    /** For determining whether light is directional or position-based */
    public boolean isDirectional() { return direction != null; }

    /** For printing light specification */
    public void print(PrintStream out)
    {
        super.print(out);

        if (direction != null)
          out.println("Direction   : " + direction   );
        if (position != null)
          out.println("Position    : " + position   );
        out.println("Color       : " + color       );
        out.println("Attenuation : " + attenuation );
    }

    //------------------------------------------------------------------------

    /** compute the resulting color at an intersection point for
     *  _this_ light, which has been tinted (from shadowing), and given
     *  the ray that led to the intersection (which can be traced back
     *  to the camera location)
     *
     * The computation does the following:
     *  - computes ambient, diffuse and specular (Phong model) illumination
     *  - uses material color (Ka, Kd, Ks) and texture
     *  - handles both directional and (attenuated) point light sources
     *    (which are affected by shadows using 'tint')
     *
     * The 'tint' is used to specify the amount of the light that is being
     * let through to the intersection point.  An exposed light has a tint
     * of (1,1,1) and an occluded light has a tint of (0,0,0) -- intermediate
     * values can result from intervening transparent objects.
     * The tint does not affect the ambient light.
     */
    Vector3d compute(ISect intersection, Vector3d tint, Ray r)
    {
        // Material for this object
        Material mat = intersection.getHitObject().getMaterialRef();
        Vector3d colorResult = new Vector3d(0.0,0.0,0.0);
        
        // adding bump map
        if (mat.hasBumpmap())
        {
        	intersection.normal = mat.getBumpmapNormal(intersection.getU(), intersection.getV());
        	//System.out.println(intersection.normal);
        }
        
        /////////////////////////////////////////////////// AMBIENT
        Vector3d ambientComponent = new Vector3d(this.getColor());
        Tools.termwiseMul3d(ambientComponent, mat.getKa());
        if (mat.hasTexture())
        {
        	Tools.termwiseMul3d(ambientComponent, mat.getTextureColor(intersection.getU(), intersection.getV()));
        }
        
        ambientComponent.clampMax(1.0); 
        colorResult.add(ambientComponent);
        
        /////////////////////////////////////////////////// DIFFUSE
        Vector3d diffuseComponent = new Vector3d(this.getColor());

        //attenuation
        double _attenuation = calculateAttenuation(this.getPosition(), intersection.getHitPoint());
        diffuseComponent.scale(_attenuation);
        
        // tint
        Tools.termwiseMul3d(diffuseComponent, tint);
                
        // Kd
        Tools.termwiseMul3d(diffuseComponent, mat.getKd());
        
        // T(u,v)
        if (mat.hasTexture())
        {
        	Tools.termwiseMul3d(diffuseComponent, mat.getTextureColor(intersection.getU(), intersection.getV()));
        }
                
        // max(0, n dot l)
        Vector3d l = null;
        if (this.isDirectional())
        {
        	l = new Vector3d(this.getDirection());
        }
        else
        {
        	l = new Vector3d();
        	l.x = this.getPosition().x - intersection.getHitPoint().x;
        	l.y = this.getPosition().y - intersection.getHitPoint().y;
        	l.z = this.getPosition().z - intersection.getHitPoint().z;
        }
        l.normalize();
        
        Vector3d n = new Vector3d(intersection.getNormal());
        n.normalize();
        double ndotl = n.dot(l);
        diffuseComponent.scale(Math.max(0.0, ndotl));
        
        diffuseComponent.clampMax(1.0);
        colorResult.add(diffuseComponent);
        
        /////////////////////////////////////////////////// SPECULAR
        if (ndotl >= 0)
        {
	        Vector3d specularComponent = new Vector3d(this.getColor());
	        
	        // attenuation
	        specularComponent.scale(_attenuation);

	        // tint
	        Tools.termwiseMul3d(specularComponent, tint);
	        
	        // Ks
	        Tools.termwiseMul3d(specularComponent, mat.getKs());
	        
	        // max(0, r dot v) ^ alpha
	        Vector3d rtmp = new Vector3d();
	        Tools.reflect(rtmp, l, intersection.getNormal());
	        rtmp.normalize();
	        
	        Vector3d v = new Vector3d(r.getDirection());
            v.normalize();
            double tmp = rtmp.dot(v);
            specularComponent.scale(Math.pow(Math.max(0, tmp), mat.getShiny()));
	        
	        specularComponent.clampMax(1.0);
	        colorResult.add(specularComponent);
	        
	        specularComponent = null;
        }
        ambientComponent = null;
        diffuseComponent = null;

        return colorResult;
    }
    
    // Light attenuation (Kc, Kl, Kq); default has no attenuation
    //    - given distance D from a light (not directional)
    //    - atten factor is 1/(Kc + Kl * D + Kq * D^2)
    //    - ambient light is not attenuated
    private double calculateAttenuation(Point3d p1, Point3d p2)
    {
    	if (this.isDirectional())
    		return 1;
    	
    	double D = p1.distance(p2);
    	
    	double Kc = this.getAttenuation().x;
    	double Kl = this.getAttenuation().y;
    	double Kq = this.getAttenuation().z;
    	
    	return 1.0 / (Kc + Kl * D + Kq * Math.pow(D, 2));
    }
}
