package com.hlag.oversigt.sources.event;

import java.util.List;

import com.hlag.oversigt.sources.event.JenkinsPipelineStatusEvent.JenkinsPipelineEventStatusItem;

import de.larssh.utils.Nullables;
import edu.umd.cs.findbugs.annotations.Nullable;

public class JenkinsPipelineStatusEvent extends ListEvent<JenkinsPipelineEventStatusItem> {
	public JenkinsPipelineStatusEvent(final List<? extends JenkinsPipelineEventStatusItem> items) {
		super(items);
	}

	public static class JenkinsPipelineEventStatusItem {
		private String pipeline;

		private String status;

		private String branch;

		private String ticket;

		private String userID;

		private String buildID;

		private String pipelineStyle;

		private String statusStyle;

		private String branchStyle;

		private String ticketStyle;

		private String userIDStyle;

		private String buildIDStyle;

		public JenkinsPipelineEventStatusItem(final String pipeline,
				final String branch,
				@Nullable final String ticket,
				@Nullable final String userID,
				final String buildID,
				final String status,
				@Nullable final String pipelineStyle,
				@Nullable final String branchStyle,
				@Nullable final String ticketStyle,
				@Nullable final String userIDStyle,
				final String buildIDStyle,
				@Nullable final String statusStyle) {
			this.pipeline = pipeline;
			this.status = status;
			this.branch = branch;
			this.ticket = Nullables.orElse(ticket, "");
			this.userID = Nullables.orElse(userID, "");
			this.buildID = buildID;

			this.pipelineStyle = Nullables.orElse(pipelineStyle, "");
			this.statusStyle = Nullables.orElse(statusStyle, "");
			this.branchStyle = Nullables.orElse(branchStyle, "");
			this.ticketStyle = Nullables.orElse(ticketStyle, "");
			this.userIDStyle = Nullables.orElse(userIDStyle, "");
			this.buildIDStyle = buildIDStyle;

		}

		public String getBranch() {
			return branch;
		}

		public String getBranchStyle() {
			return branchStyle;
		}

		public String getPipeline() {
			return pipeline;
		}

		public String getPipelineStyle() {
			return pipelineStyle;
		}

		public String getStatus() {
			return status;
		}

		public String getStatusStyle() {
			return statusStyle;
		}

		public String getTicket() {
			return ticket;
		}

		public String getTicketStyle() {
			return ticketStyle;
		}

		public String getUserID() {
			return userID;
		}

		public String getUserIDStyle() {
			return userIDStyle;
		}

		public String getBuildID() {
			return buildID;
		}

		public String getBuildIDStyle() {
			return buildIDStyle;
		}
	}
}
