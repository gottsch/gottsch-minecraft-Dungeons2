/**
 * 
 */
package com.someguyssoftware.dungeons2;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import java.util.Random;

import javax.swing.JFrame;
import javax.swing.JPanel;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.someguyssoftware.dungeons2.builder.DungeonBuilderTopDown;
import com.someguyssoftware.dungeons2.builder.FloorBuilder;
import com.someguyssoftware.dungeons2.builder.LevelBuilder;
import com.someguyssoftware.dungeons2.graph.Wayline;
import com.someguyssoftware.dungeons2.graph.mst.Edge;
import com.someguyssoftware.dungeons2.model.AboveRoom;
import com.someguyssoftware.dungeons2.model.Dimensions;
import com.someguyssoftware.dungeons2.model.Dungeon;
import com.someguyssoftware.dungeons2.model.DungeonConfig;
import com.someguyssoftware.dungeons2.model.Floor;
import com.someguyssoftware.dungeons2.model.FloorConfig;
import com.someguyssoftware.dungeons2.model.IRoom;
import com.someguyssoftware.dungeons2.model.Level;
import com.someguyssoftware.dungeons2.model.LevelConfig;
import com.someguyssoftware.dungeons2.model.Room;
import com.someguyssoftware.gottschcore.Quantity;
import com.someguyssoftware.gottschcore.positional.Coords;
import com.someguyssoftware.gottschcore.positional.ICoords;
import com.someguyssoftware.gottschcore.random.RandomHelper;

/**
 * 
 * @author Mark Gottschling on Sep 7, 2017
 *
 */
public class FloorVisualizer {
	private static final Logger logger = LogManager.getLogger(FloorVisualizer.class);

	FloorBuilder builder = new FloorBuilder();
//	FloorConfig config = new FloorConfig();
	
	// seed for random
	long seed = new Random().nextInt(10000)-5000;
	
	/**
	 * the starting spawn point from where all rooms are generated
	 */
	ICoords startPoint = new Coords(500, 100, 500);
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		logger.info("Starting...");
		
		FloorVisualizer visualizer = new FloorVisualizer();
		
		// set the seed if one is provided
		if (args.length ==1) {
			visualizer.seed = Long.valueOf(args[0]);
		}
		Random random = new Random(visualizer.seed);
		
		FloorConfig config = new FloorConfig();
		config.setMinecraftConstraintsOn(false);
		
		// set the number of rooms
		config.setNumberOfRooms(new Quantity(1, 5));
		double factor = 3.2;
		config.setWidth(new Quantity(7*factor, 15*factor));
		config.setDepth(new Quantity(7*factor, 15*factor));
		config.setHeight(new Quantity(7, 6));
		
		// epicenter style settings
		// NOTE epicenter style needs to have a smaller distance, else you get a lot of long hallways
		config.setXDistance(new Quantity(-15, 15));
		config.setZDistance(new Quantity(-15, 15));
		
//		int r = RandomHelper.randomInt(15, 25);
//		test.config.setNumberOfRooms(new Quantity(r, r));
//		test.config.setXDistance(new Quantity(-(r+10), (r*2.5)));
//		test.config.setZDistance(new Quantity(-(r*1.5), r*1.5));

//		test.config.setXOffset(new Quantity(50, 50));
//		test.config.setZOffset(new Quantity(50, 50));		
		// pancake style settings		
//		test.config.setXDistance(new Quantity(0, 100));
//		test.config.setZDistance(new Quantity(0, 300));
//		test.config.setXOffset(new Quantity(50, 50));
//		test.config.setYOffset(new Quantity(150, 150));
		
//		visualizer.config.setMinecraftConstraintsOn(false);
//		visualizer.config.setSupportOn(false);
//		visualizer.builder.setConfig(visualizer.config);

		visualizer.builder.setConfig(config);
		
		// create a start room
		IRoom startRoom = new AboveRoom();
		startRoom.setDimensions(new Dimensions(30, 7, 30));
		startRoom.setCoords(visualizer.startPoint.add(-15, 0, -15));
		startRoom.setAnchor(true);
		
		Floor floor = visualizer.builder.build(null, random, startRoom);
		
		System.out.println(floor);
		
		// draw out rectangles
		JFrame window = new JFrame();
		JPanel panel = new FloorVisualizer().new VisualFloorPanel(visualizer.builder, floor);
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		window.setBounds(0, 0, 1000, 1000);
		window.add(panel);
		window.setVisible(true);
//		window.pack();

	}

	/**
	 * 
	 * @author Mark Gottschling on Jul 22, 2016
	 *
	 */
	class VisualFloorPanel extends JPanel {
		FloorBuilder builder;
		Floor floor;
		
		public VisualFloorPanel(FloorBuilder builder, Floor floor) {
			super();
			this.builder = builder;
			this.floor = floor;
		}
		
		/* (non-Javadoc)
		 * @see javax.swing.JComponent#paintComponent(java.awt.Graphics)
		 */
		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			// create 2D graphics
			Graphics2D g2d = (Graphics2D)g.create();

			// setup the rendering hints
	        RenderingHints rh =
	            new RenderingHints(RenderingHints.KEY_ANTIALIASING, 
	            RenderingHints.VALUE_ANTIALIAS_ON);

	        rh.put(RenderingHints.KEY_RENDERING,
	               RenderingHints.VALUE_RENDER_QUALITY);

	        g2d.setRenderingHints(rh);

	        // setup the title 
	        g2d.setFont(new Font("Verdana", Font.BOLD, 14));
	        String title = "Dungeons2! Floor Visualizer";

	        g2d.drawString(title, 200, 15);
	        
	        // setup the config properties title
	        g2d.setFont(new Font("Verdana", Font.BOLD, 10));
	        g2d.drawString("Config Properties", 10, 10);
	        
	        // setup thte config properties
	        FloorConfig config = builder.getConfig();
	        g2d.setFont(new Font("Verdana", Font.PLAIN, 9));
	        g2d.setColor(Color.BLACK);
	        g2d.drawString("# of Rooms: " + config.getNumberOfRooms().getMinInt()+" to "+config.getNumberOfRooms().getMaxInt(), 10, 20);
	        g2d.drawString("Width (X): "+ config.getWidth().getMinInt()+" to "+config.getWidth().getMaxInt(), 10, 30);
	        g2d.drawString("Height (Y): "+ config.getHeight().getMinInt()+" to "+config.getHeight().getMaxInt(), 10, 40);
	        g2d.drawString("Depth (Z): "+ config.getDepth().getMinInt()+" to "+config.getDepth().getMaxInt(), 10, 50);
			g2d.drawString("XDistance: "+ config.getXDistance().getMinInt()+" to "+config.getXDistance().getMaxInt(), 10, 60);
			g2d.drawString("ZDistance: "+ config.getZDistance().getMinInt()+" to "+config.getZDistance().getMaxInt(), 10, 70);

			g2d.drawString("Random Seed: "+ seed, 10, 120);
			g2d.drawString("Start Point: " + floor.getRooms().get(0).getCenter().toShortString(), 10, 130);
			
	        // setup the generated properties title
	        g2d.setFont(new Font("Verdana", Font.BOLD, 10));
	        g2d.drawString("Generated Properties", 10, 150);
	        g2d.setFont(new Font("Verdana", Font.PLAIN, 9));
	        g2d.drawString("# of Rooms: " + floor.getRooms().size(), 10, 160);

			// draw legend
			g2d.setColor(Color.BLACK);
			g2d.fillRoundRect(10, 140+300, 10, 10, 3, 3);
			g2d.setColor(Color.CYAN);
			g2d.fillRoundRect(10, 152+300, 10, 10, 3, 3);
			g2d.setColor(Color.YELLOW);
			g2d.fillRoundRect(10, 164+300, 10, 10, 3, 3);
			g2d.setColor(Color.MAGENTA);
			g2d.fillRoundRect(10, 176+300, 10, 10, 3, 3);
			g2d.setColor(Color.RED);
			g2d.fillRoundRect(10, 188+300, 10, 10, 3, 3);
			
			g2d.setColor(Color.BLACK);
			g2d.drawString("y = base", 23, 147+300);
			g2d.drawString("y = +1", 23, 159+300);
			g2d.drawString("y = +2", 23, 171+300);
			g2d.drawString("y = +3", 23, 183+300);
			g2d.drawString("y = >+3", 23, 195+300);
			
			// draw scale on the side & bottom
			g2d.drawLine(10, 200, 10, 400);
			g2d.drawLine(200, 500, 400, 500);
			// annotate the line
			g2d.drawString("-100", 12, 200);
			g2d.drawString("0", 12, 300);
			g2d.drawString("100", 12, 400);
			g2d.drawString("-100", 200, 510);
			g2d.drawString("0", 300, 510);
			g2d.drawString("100", 400, 510);
			
			// draw the center point
			g2d.drawOval(500-5, 500-5, 10, 10);
			
			List<IRoom> rooms = floor.getRooms();
				for (IRoom room : rooms) {
//				char[] chars = String.valueOf(room.getId()).toCharArray();

//				if (room.isStart()) {
//					g2d.setColor(Color.GREEN);
//				}
//				else if (room.isEnd()) {
//					g2d.setColor(Color.RED);					
//				}
//				else {					
////					g2d.setColor(Color.BLACK);
//					g2d.setColor(new Color(130, 100, 84));
//				}
				g2d.setColor(new Color(130, 100, 84));
				g2d.fillRoundRect(room.getCoords().getX(), room.getCoords().getZ(), room.getDimensions().getWidth(), room.getDimensions().getDepth(), 3, 3);
				
				// draw the border
				if (room.getCoords().getY() == startPoint.getY()) {
					g2d.setColor(Color.BLACK);
				}
				else if (room.getCoords().getY() == startPoint.getY() + 1) {
					g2d.setColor(Color.CYAN);
				}
				else if (room.getCoords().getY() == startPoint.getY() + 2) {
					g2d.setColor(Color.YELLOW);
				}
				else if (room.getCoords().getY() == startPoint.getY() + 3) {
					g2d.setColor(Color.MAGENTA);					
				}
				else {
					g2d.setColor(Color.RED);
//					logger.info("Red Room coords:" + room.getCoords().toShortString());
				}
				g2d.drawRoundRect(room.getCoords().getX(), room.getCoords().getZ(), room.getDimensions().getWidth(), room.getDimensions().getDepth(), 3, 3);
				
				// draw the room #
				g2d.setColor(Color.BLACK);
				g2d.drawString(String.valueOf(room.getID()), room.getCoords().getX()+3, room.getCoords().getZ()+11);
				g2d.setColor(Color.WHITE);
				g2d.drawString(String.valueOf(room.getID()), room.getCoords().getX()+2, room.getCoords().getZ()+10);
//				// draw the # of degrees (paths)
//				g2d.setColor(Color.BLACK);
//				g2d.drawString(String.valueOf(room.getDegrees()), room.getCoords().getX()+3, room.getCoords().getZ()+23);
//				g2d.setColor(Color.CYAN);
//				g2d.drawString(String.valueOf(room.getDegrees()), room.getCoords().getX()+2, room.getCoords().getZ()+22);		
			}


			
//			// draw all edges
//			for (Edge e : level.getEdges()) {
//				if (e.v < rooms.size() && e.w < rooms.size()) {
//					Room room1 = rooms.get(e.v);
//					Room room2 = rooms.get(e.w);				
//					g.drawLine(room1.getCenter().getX(), room1.getCenter().getZ(), room2.getCenter().getX(), room2.getCenter().getZ());
//				}
//			}

//			g2d.setColor(Color.BLUE);
//			for (Wayline w : level.getWaylines()) {
//				g2d.drawLine(w.getPoint1().getX(), w.getPoint1().getZ(), w.getPoint2().getX(), w.getPoint2().getZ());
//			}

		}
	}
}
