/*
 *    This file is part of Slambench-Java: A (serial) Java version of the SLAMBENCH computer vision benchmark suite
 *    https://github.com/beehive-lab/slambench-java
 *
 *    Copyright (c) 2013-2019 APT Group, School of Computer Science,
 *    The University of Manchester
 *
 *    This work is partially supported by EPSRC grants:
 *    Anyscale EP/L000725/1 and PAMELA EP/K008730/1.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 *    Authors: James Clarkson
 */
package kfusion.java.common;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class AbstractLogger {
		
	private Logger logger =  LogManager.getLogger(this.getClass());
			
	protected void info(String msg){
		logger.info(msg);
	}
	
	protected void info(String pattern,Object... args){
		info(String.format(pattern,args));
	}
	
	protected void debug(String msg){
		logger.debug(msg);
	}
	
	protected void debug(String pattern,Object... args){
		debug(String.format(pattern,args));
	}
	
	protected void warn(String pattern,Object... args){
		warn(String.format(pattern, args));
	}
	
	protected void warn(String msg){
		logger.warn(msg);
	}
	
	protected void fatal(String pattern,Object... args){
		fatal(String.format(pattern, args));
	}
	
	protected void fatal(String msg){
		logger.fatal(msg);
	}
	
	protected void error(String pattern,Object... args){
		error(String.format(pattern, args));
	}
	
	protected void error(String msg){
		logger.error(msg);
	}
	
	protected void trace(String pattern,Object... args){
		trace(String.format(pattern,args));
	}
	
	protected void trace(String msg){
		logger.trace(msg);
	}
	
	
	
	
	

}
