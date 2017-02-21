package org.teamresistance.frc.util;

import java.util.ArrayList;

import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;
import org.teamresistance.frc.IO;
import org.teamresistance.frc.vision.Pipeline;

import edu.wpi.cscore.AxisCamera;
import edu.wpi.first.wpilibj.CameraServer;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj.vision.VisionThread;

public class AutoTargetFollow {
	// PID constants
	private double kP; // Proportional constant
	private double kI = 0.0; // Integral constant
	private double kD = 0.0; // Derivative constant
	
	// PID variables
	private double prevError = 0.0; // The error from the previous loop
	private double integral = 0.0; // Error integrated over time
	
	private long prevTime;
	
	private double tolerance = 0.1; // The percent tolerance for the error to be considered on target
	
	private double maxOutput = 1.0;
	private double minOutput = -1.0;
	
	private Pipeline p;
	
	private double imageWidth = 640;
	private double imageHeight = 480;
	
	private double centerX;
	private double centerY;
	
	private VisionThread visionThread;
	
	private Object imgLock;
	
	public AutoTargetFollow() {
		imgLock = new Object();
		AxisCamera camera = CameraServer.getInstance().addAxisCamera("http://10.0.86.20/mjpg/video.mjpg");
		visionThread = new VisionThread(camera, new Pipeline(), pipeline -> {
	        if (!pipeline.filterContoursOutput().isEmpty()) {
	            Rect r = Imgproc.boundingRect(pipeline.filterContoursOutput().get(0));
	            synchronized (imgLock) {
	                centerX = r.x + (r.width / 2);
	                centerY = r.y + (r.height / 2);
	            }
	        }
	    });
	}
	
	public void init(double p, double i, double d) {
		this.kP = p;
		this.kI = i;
		this.kD = d;
		this.prevError = 0.0;
		this.integral = 0.0;
		this.prevTime = System.currentTimeMillis();
	}
	
	public void update() {
		double error;
		synchronized (imgLock) {
			error = imageWidth - centerY;
			SmartDashboard.putNumber("Center Y AutoTargetFollow", centerY);
			SmartDashboard.putNumber("Center X AutoTargetFollow", centerX);
			SmartDashboard.putNumber("Error AutoTargetFollow", error);
		}
		
		long curTime = System.currentTimeMillis(); 
		double deltaTime = (curTime - prevTime) / 1000.0;
		
		if(Math.abs(error) >= 300) {
			if(error > 0) {
				error -= 360;
			} else {
				error += 360;
			}
		}
		
		if(onTarget(error)) error = 0.0;
		integral += error;
		
		double result = (error * kP) + (integral * kI * deltaTime) + ((error - prevError) * kD / deltaTime);
		prevError = error;
		
		if(result > maxOutput) result = maxOutput;
		else if(result < minOutput) result = minOutput;
		
		IO.drive.getDrive().mecanumDrive_Cartesian(JoystickIO.leftJoystick.getX(), JoystickIO.leftJoystick.getY(), result, IO.navX.getYaw());
	}
	
	// If the error is less than or equal to the tolerance it is on target
	private boolean onTarget(double error) {
		return Math.abs(error) <= tolerance;
	}
}