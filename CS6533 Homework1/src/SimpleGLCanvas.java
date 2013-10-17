import javax.media.opengl.*;

import com.sun.opengl.util.*;

import java.awt.event.*;
import java.awt.*;

/* class SimpleGLCanvas
 * An extension of a OpenGL API (JSR-231) GLCanvas that creates an OpenGL
 * drawing area with RGB color and a depth buffer, together with an 
 * animator controling whether the animation is on.
 * 
 * Use the OpenGL API from the abstract methods init(), draw() and projection()
 *
 * Doug DeCarlo 9/04
 * Xiaofeng Mi  9/06  (revised for JOGL)
 */

public abstract class SimpleGLCanvas extends GLCanvas 
    implements GLEventListener
{
    private Animator animator;
    
    // Constructor
    public SimpleGLCanvas(Window parent)
    {
    	animator = new Animator(this);
    	parent.addWindowListener(new WindowAdapter() {
                public void windowClosing(WindowEvent e) {
                    // Run this on another thread than the AWT event queue to
                    // make sure the call to Animator.stop() completes before
                    // exiting
                    new Thread(new Runnable() {
                            public void run() {
                                if (animator.isAnimating())
                		  animator.stop();
                                System.exit(0);
                            }
                        }).start();
                }
            });

    	addGLEventListener(this);
        
        // Reset timer
        resetClock();
    }
    
    // ------------------------------------------------------------
    // Animation clock
    
    // Starting time of program
    public long startingTime;
    
    // Record starting time of program
    public void resetClock()
    {
        startingTime = System.currentTimeMillis();
    }
    
    // Return current time (in seconds) since program started
    public double readClock()
    {
        return (System.currentTimeMillis() - startingTime) / 1000.0;
    }
    
    // Check if animation is proceeding
    public boolean isAnimated()
    {
        return animator.isAnimating();
    }
    
    // Start/stop animation
    public void setAnimation(boolean on)
    {
    	if (on)
          animator.start();
    	else
          animator.stop();
    }
    
    // ------------------------------------------------------------
    // GLEventListener
    public void init(GLAutoDrawable drawable) {
    	init(drawable.getGL());
    }

    public void reshape(GLAutoDrawable drawable, int x, int y,
                        int width, int height)
    {
    	// Specify new projection for this window
    	projection(drawable.getGL(), width, height);
    }
    
    public void display(GLAutoDrawable drawable) {
        // Draw the contents of the window (abstract method)
        draw(drawable.getGL());
    }
    
    public void displayChanged(GLAutoDrawable drawable,
                               boolean modeChanged, boolean deviceChanged) {
    }
    
    // ---------------------------------------------------------------------
    // Drawing and projection functions
    
    // OpenGL Initialization
    abstract public void init(GL gl);
    
    // Called when window is created or whenever it is resized
    abstract public void projection(GL gl,
                                    int width, int height);
    
    // Called whenever window needs to be redrawn
    abstract public void draw(GL gl);
}
