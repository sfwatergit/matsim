package playground.anhorni.locationchoice.cs.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import org.apache.log4j.Logger;
import org.matsim.gbl.Gbl;
import org.matsim.utils.io.IOUtils;
import playground.anhorni.locationchoice.cs.helper.ChoiceSet;
import playground.anhorni.locationchoice.cs.helper.ZHFacility;

public class ChoiceSetWriterSimple extends CSWriter {

	private final static Logger log = Logger.getLogger(ChoiceSetWriterSimple.class);
	
	public ChoiceSetWriterSimple() {
	}
	
	public void write(String outdir, String name,List<ChoiceSet> choiceSets)  {
		
		String outfile = outdir + name + "_ChoiceSets.txt";
		String outfile_alternatives = outdir + name + "_NumberOfAlternatives.txt";
		
		if (!super.checkBeforeWriting(choiceSets)) {
			log.warn(outfile +" not created");
			return;
		}
		
		try {		
			final String header="Id\tTTB (s)\tShop_id\tLink_x\tLink_y\tExact_x\tExact_y\tTravel_Time (s)\tTravel_Distance (m)\tChosen";
						
			final BufferedWriter out = IOUtils.getBufferedWriter(outfile);
			final BufferedWriter out_alternatives = IOUtils.getBufferedWriter(outfile_alternatives);
			out.write(header);
			out.newLine();
			out_alternatives.write("Id\tNumber of alternatives");
			out_alternatives.newLine();			
			
			Iterator<ChoiceSet> choiceSet_it = choiceSets.iterator();
			while (choiceSet_it.hasNext()) {
				ChoiceSet choiceSet = choiceSet_it.next();
				
				boolean oneIsChosen = false;
				String location;
				Iterator<ZHFacility> fac_it = choiceSet.getFacilities().iterator();
				while (fac_it.hasNext()) {
					ZHFacility facility = fac_it.next();
					
					String chosen;
					if (facility.getId().compareTo(choiceSet.getChosenZHFacility().getId()) == 0) {
						chosen = "1";
						oneIsChosen = true;
					}
					else {
						chosen = "0";
					}										
					location = facility.getId() + "\t" + facility.getCenter().getX() +"\t" + 
						facility.getCenter().getY()+  "\t" + facility.getExactPosition().getX() + "\t" +
						facility.getExactPosition().getY() + "\t" +
						choiceSet.getTravelTime(facility) + "\t" +
						choiceSet.getTravelDistance(facility) + "\t" +
						chosen;
					
					out.write(choiceSet.getId() +"\t" + 
							choiceSet.getTravelTimeBudget() + "\t" +
							location);
					out.newLine();
					
					
				}
				out.flush();
				out_alternatives.write(choiceSet.getId() + "\t" + choiceSet.getFacilities().size());
				out_alternatives.newLine();
				out_alternatives.flush();
				
				if (!oneIsChosen) {
					log.error("Problem with choice set " + choiceSet.getId());
				}
			}
			out.flush();
			out.close();
			out_alternatives.close();
						
		} catch (final IOException e) {
				Gbl.errorMsg(e);
		}	
	}
}
