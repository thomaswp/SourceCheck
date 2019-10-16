package edu.isnap.sourcecheck.edit;

import java.util.Comparator;

import edu.isnap.node.ASTNode;
import edu.isnap.node.ASTNode.SourceLocation;

public class EditSorter implements Comparator<EditHint> {
    public int compare(EditHint a, EditHint b) {
    	SourceLocation a_start = null;
    	SourceLocation a_end = null;
    	SourceLocation b_start = null;
    	SourceLocation b_end = null;
    	
    	if (a instanceof Insertion) {
    		a_start = getInsertionStart(a);
    		a_end = getInsertionEnd(a);
    	} else if (a instanceof Deletion) {
    		a_start = getDeletionStart(a);
    		a_end = getDeletionEnd(a);
    	} else if(a instanceof Reorder) {
    		throw new RuntimeException();
    	}
    	
    	if (b instanceof Insertion) {
    		b_start = getInsertionStart(b);
    		b_end = getInsertionEnd(b);
    	} else if (b instanceof Deletion) {
    		b_start = getDeletionStart(b);
    		b_end = getDeletionEnd(b);
    	} else if(b instanceof Reorder) {
    		throw new RuntimeException();
    	}
        return a_end.compareTo(b_end);
    }
    
    private SourceLocation getInsertionStart(EditHint hint) {
    	Insertion insertion = (Insertion)(hint);
    	if(insertion.replaced != null && insertion.replaced.tag instanceof ASTNode) {
    		return((ASTNode)(insertion.replaced.tag)).startSourceLocation;
    	}
    	if(insertion.candidate != null && insertion.candidate.tag instanceof ASTNode) {
    		return ((ASTNode)(insertion.candidate.tag)).startSourceLocation;
    	}
    	if(insertion.parent != null && insertion.parent.tag instanceof ASTNode) {
    		return ((ASTNode)(insertion.parent.tag)).startSourceLocation;
    	}
    	return null;
    }
    
    private SourceLocation getInsertionEnd(EditHint hint) {
    	Insertion insertion = (Insertion)(hint);
    	if(insertion.replaced != null && insertion.replaced.tag instanceof ASTNode) {
    		return((ASTNode)(insertion.replaced.tag)).endSourceLocation;
    	}
    	if(insertion.candidate != null && insertion.candidate.tag instanceof ASTNode) {
    		return ((ASTNode)(insertion.candidate.tag)).endSourceLocation;
    	}
    	if(insertion.parent != null && insertion.parent.tag instanceof ASTNode) {
    		return ((ASTNode)(insertion.parent.tag)).endSourceLocation;
    	}
    	return null;
    }
    
    private SourceLocation getDeletionStart(EditHint hint) {
    	Deletion deletion = (Deletion)(hint);
    	if(deletion.node != null && deletion.node.tag instanceof ASTNode) {
    		return ((ASTNode)(deletion.node.tag)).startSourceLocation;
    	}
    	if(deletion.parent != null && deletion.parent.tag instanceof ASTNode) {
    		return ((ASTNode)(deletion.parent.tag)).startSourceLocation;
    	}
    	return null;
    }
    
    private SourceLocation getDeletionEnd(EditHint hint) {
    	Deletion deletion = (Deletion)(hint);
    	if(deletion.node != null && deletion.node.tag instanceof ASTNode) {
    		return ((ASTNode)(deletion.node.tag)).endSourceLocation;
    	}
    	if(deletion.parent != null && deletion.parent.tag instanceof ASTNode) {
    		return ((ASTNode)(deletion.parent.tag)).endSourceLocation;
    	}
    	return null;
    }
}
