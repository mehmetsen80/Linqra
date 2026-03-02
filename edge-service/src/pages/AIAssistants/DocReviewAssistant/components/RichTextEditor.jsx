import React, { useImperativeHandle, forwardRef } from 'react';
import { useEditor, EditorContent } from '@tiptap/react';
import { Extension } from '@tiptap/core';
import { showSuccessToast, showErrorToast } from '../../../../utils/toastConfig';
import StarterKit from '@tiptap/starter-kit';
import Highlight from '@tiptap/extension-highlight';
import Underline from '@tiptap/extension-underline';
import { TextStyle } from '@tiptap/extension-text-style';
import { FontFamily } from '@tiptap/extension-font-family';
import TextAlign from '@tiptap/extension-text-align';
import { Table, TableRow, TableCell, TableHeader } from '@tiptap/extension-table';
import { Box, Paper, ToggleButton, ToggleButtonGroup, Divider, Toolbar } from '@mui/material';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import FormControl from '@mui/material/FormControl';
import {
    FormatBold,
    FormatItalic,
    FormatUnderlined,
    FormatStrikethrough,
    FormatQuote,
    FormatListBulleted,
    FormatListNumbered,
    FormatAlignLeft,
    FormatAlignCenter,
    FormatAlignRight,
    FormatAlignJustify,
    Code,
    HorizontalRule,
    Undo,
    Redo
} from '@mui/icons-material';

const FontSize = Extension.create({
    name: 'fontSize',
    addOptions() {
        return {
            types: ['textStyle'],
        };
    },
    addGlobalAttributes() {
        return [
            {
                types: this.options.types,
                attributes: {
                    fontSize: {
                        default: null,
                        parseHTML: element => element.style.fontSize.replace(/['"]+/g, ''),
                        renderHTML: attributes => {
                            if (!attributes.fontSize) {
                                return {};
                            }
                            return {
                                style: `font-size: ${attributes.fontSize}`,
                            };
                        },
                    },
                },
            },
        ];
    },
    addCommands() {
        return {
            setFontSize: fontSize => ({ chain }) => {
                return chain()
                    .setMark('textStyle', { fontSize })
                    .run();
            },
            unsetFontSize: () => ({ chain }) => {
                return chain()
                    .setMark('textStyle', { fontSize: null })
                    .removeEmptyTextStyle()
                    .run();
            },
        };
    },
});

const MenuBar = ({ editor }) => {
    if (!editor) {
        return null;
    }

    return (
        <Paper elevation={0} sx={{ borderBottom: 1, borderColor: 'divider', mb: 2 }}>
            <Toolbar variant="dense" sx={{ flexWrap: 'wrap', gap: 1, py: 1 }}>

                {/* Font Family Dropdown */}
                <FormControl size="small" sx={{ minWidth: 120 }}>
                    <Select
                        value={editor.getAttributes('textStyle').fontFamily || 'Inter'}
                        onChange={(event) => {
                            const value = event.target.value;
                            editor.chain().focus().setFontFamily(value).run();
                        }}
                        displayEmpty
                        inputProps={{ 'aria-label': 'Font Family' }}
                        MenuProps={{ disablePortal: true }}
                        sx={{
                            height: 40,
                            '& .MuiSelect-select': {
                                py: 1,
                                fontFamily: editor.getAttributes('textStyle').fontFamily || 'Inter, sans-serif'
                            }
                        }}
                    >
                        <MenuItem value="Inter" sx={{ fontFamily: 'Inter' }}>Inter</MenuItem>
                        <MenuItem value="Arial" sx={{ fontFamily: 'Arial' }}>Arial</MenuItem>
                        <MenuItem value="Helvetica" sx={{ fontFamily: 'Helvetica' }}>Helvetica</MenuItem>
                        <MenuItem value="Verdana" sx={{ fontFamily: 'Verdana' }}>Verdana</MenuItem>
                        <MenuItem value="Tahoma" sx={{ fontFamily: 'Tahoma' }}>Tahoma</MenuItem>
                        <MenuItem value="Trebuchet MS" sx={{ fontFamily: 'Trebuchet MS' }}>Trebuchet MS</MenuItem>
                        <MenuItem value="Times New Roman" sx={{ fontFamily: 'Times New Roman' }}>Times New Roman</MenuItem>
                        <MenuItem value="Georgia" sx={{ fontFamily: 'Georgia' }}>Georgia</MenuItem>
                        <MenuItem value="Courier New" sx={{ fontFamily: 'Courier New' }}>Courier New</MenuItem>
                        <MenuItem value="Impact" sx={{ fontFamily: 'Impact' }}>Impact</MenuItem>
                        <MenuItem value="Comic Sans MS" sx={{ fontFamily: 'Comic Sans MS' }}>Comic Sans MS</MenuItem>
                    </Select>
                </FormControl>

                <Divider flexItem orientation="vertical" sx={{ mx: 0.5, my: 1 }} />

                {/* Font Size Dropdown */}
                <FormControl size="small" sx={{ minWidth: 80 }}>
                    <Select
                        value={editor.getAttributes('textStyle').fontSize || ''}
                        onChange={(event) => {
                            const value = event.target.value;
                            if (value) {
                                editor.chain().focus().setFontSize(value).run();
                            } else {
                                editor.chain().focus().unsetFontSize().run();
                            }
                        }}
                        displayEmpty
                        inputProps={{ 'aria-label': 'Font Size' }}
                        MenuProps={{ disablePortal: true }}
                        sx={{ height: 40, '& .MuiSelect-select': { py: 1 } }}
                        renderValue={(selected) => {
                            if (!selected) {
                                return <em>Size</em>;
                            }
                            return selected.replace('px', '');
                        }}
                    >
                        <MenuItem value=""><em>Default</em></MenuItem>
                        <MenuItem value="8px">8</MenuItem>
                        <MenuItem value="10px">10</MenuItem>
                        <MenuItem value="12px">12</MenuItem>
                        <MenuItem value="14px">14</MenuItem>
                        <MenuItem value="16px">16</MenuItem>
                        <MenuItem value="18px">18</MenuItem>
                        <MenuItem value="20px">20</MenuItem>
                        <MenuItem value="24px">24</MenuItem>
                        <MenuItem value="30px">30</MenuItem>
                        <MenuItem value="36px">36</MenuItem>
                        <MenuItem value="48px">48</MenuItem>
                        <MenuItem value="60px">60</MenuItem>
                        <MenuItem value="72px">72</MenuItem>
                        <MenuItem value="96px">96</MenuItem>
                    </Select>
                </FormControl>

                <Divider flexItem orientation="vertical" sx={{ mx: 0.5, my: 1 }} />

                <ToggleButtonGroup
                    size="small"
                    value={[
                        editor.isActive('bold') && 'bold',
                        editor.isActive('italic') && 'italic',
                        editor.isActive('underline') && 'underline',
                        editor.isActive('strike') && 'strike',
                        editor.isActive('code') && 'code'
                    ].filter(Boolean)}
                >
                    <ToggleButton
                        value="bold"
                        onClick={() => editor.chain().focus().toggleBold().run()}
                        aria-label="bold"
                    >
                        <FormatBold fontSize="small" />
                    </ToggleButton>
                    <ToggleButton
                        value="italic"
                        onClick={() => editor.chain().focus().toggleItalic().run()}
                        aria-label="italic"
                    >
                        <FormatItalic fontSize="small" />
                    </ToggleButton>
                    <ToggleButton
                        value="underline"
                        onClick={() => editor.chain().focus().toggleUnderline().run()}
                        aria-label="underline"
                    >
                        <FormatUnderlined fontSize="small" />
                    </ToggleButton>
                    <ToggleButton
                        value="strike"
                        onClick={() => editor.chain().focus().toggleStrike().run()}
                        aria-label="strikethrough"
                    >
                        <FormatStrikethrough fontSize="small" />
                    </ToggleButton>
                    <ToggleButton
                        value="code"
                        onClick={() => editor.chain().focus().toggleCode().run()}
                        aria-label="code"
                    >
                        <Code fontSize="small" />
                    </ToggleButton>
                </ToggleButtonGroup>

                <Divider flexItem orientation="vertical" sx={{ mx: 0.5, my: 1 }} />

                <FormControl size="small" sx={{ minWidth: 120 }}>
                    <Select
                        value={
                            editor.isActive('heading', { level: 1 }) ? 'h1' :
                                editor.isActive('heading', { level: 2 }) ? 'h2' :
                                    editor.isActive('heading', { level: 3 }) ? 'h3' :
                                        editor.isActive('heading', { level: 4 }) ? 'h4' :
                                            editor.isActive('heading', { level: 5 }) ? 'h5' :
                                                editor.isActive('heading', { level: 6 }) ? 'h6' :
                                                    'p'
                        }
                        onChange={(event) => {
                            const value = event.target.value;
                            if (value === 'p') {
                                editor.chain().focus().setParagraph().run();
                            } else if (value.startsWith('h')) {
                                const level = parseInt(value.replace('h', ''));
                                editor.chain().focus().toggleHeading({ level }).run();
                            }
                        }}
                        displayEmpty
                        inputProps={{ 'aria-label': 'Text style' }}
                        MenuProps={{ disablePortal: true }} // Fix for potential z-index/portal issues
                        sx={{ height: 40, '& .MuiSelect-select': { py: 1 } }}
                    >
                        <MenuItem value="p">Normal Text</MenuItem>
                        <MenuItem value="h1" sx={{ fontSize: '2em', fontWeight: 'bold' }}>Heading 1</MenuItem>
                        <MenuItem value="h2" sx={{ fontSize: '1.5em', fontWeight: 'bold' }}>Heading 2</MenuItem>
                        <MenuItem value="h3" sx={{ fontSize: '1.17em', fontWeight: 'bold' }}>Heading 3</MenuItem>
                        <MenuItem value="h4" sx={{ fontSize: '1em', fontWeight: 'bold' }}>Heading 4</MenuItem>
                        <MenuItem value="h5" sx={{ fontSize: '0.83em', fontWeight: 'bold' }}>Heading 5</MenuItem>
                        <MenuItem value="h6" sx={{ fontSize: '0.67em', fontWeight: 'bold' }}>Heading 6</MenuItem>
                    </Select>
                </FormControl>

                <Divider flexItem orientation="vertical" sx={{ mx: 0.5, my: 1 }} />

                <ToggleButtonGroup
                    size="small"
                    value={[
                        editor.isActive('bulletList') && 'bulletList',
                        editor.isActive('orderedList') && 'orderedList',
                        editor.isActive('blockquote') && 'blockquote'
                    ].filter(Boolean)}
                >
                    <ToggleButton
                        value="bulletList"
                        onClick={() => editor.chain().focus().toggleBulletList().run()}
                        aria-label="bullet list"
                    >
                        <FormatListBulleted fontSize="small" />
                    </ToggleButton>
                    <ToggleButton
                        value="orderedList"
                        onClick={() => editor.chain().focus().toggleOrderedList().run()}
                        aria-label="ordered list"
                    >
                        <FormatListNumbered fontSize="small" />
                    </ToggleButton>
                    <ToggleButton
                        value="blockquote"
                        onClick={() => editor.chain().focus().toggleBlockquote().run()}
                        aria-label="blockquote"
                    >
                        <FormatQuote fontSize="small" />
                    </ToggleButton>
                </ToggleButtonGroup>

                <ToggleButtonGroup size="small" sx={{ ml: 0.5 }}>
                    <ToggleButton
                        value="horizontalRule"
                        onClick={() => editor.chain().focus().setHorizontalRule().run()}
                        aria-label="horizontal rule"
                    >
                        <HorizontalRule fontSize="small" />
                    </ToggleButton>
                </ToggleButtonGroup>

                <Divider flexItem orientation="vertical" sx={{ mx: 0.5, my: 1 }} />

                <ToggleButtonGroup
                    size="small"
                    exclusive
                    value={
                        editor.isActive({ textAlign: 'left' }) ? 'left' :
                            editor.isActive({ textAlign: 'center' }) ? 'center' :
                                editor.isActive({ textAlign: 'right' }) ? 'right' :
                                    editor.isActive({ textAlign: 'justify' }) ? 'justify' : 'left'
                    }
                >
                    <ToggleButton
                        value="left"
                        onClick={() => editor.chain().focus().setTextAlign('left').run()}
                        aria-label="align left"
                    >
                        <FormatAlignLeft fontSize="small" />
                    </ToggleButton>
                    <ToggleButton
                        value="center"
                        onClick={() => editor.chain().focus().setTextAlign('center').run()}
                        aria-label="align center"
                    >
                        <FormatAlignCenter fontSize="small" />
                    </ToggleButton>
                    <ToggleButton
                        value="right"
                        onClick={() => editor.chain().focus().setTextAlign('right').run()}
                        aria-label="align right"
                    >
                        <FormatAlignRight fontSize="small" />
                    </ToggleButton>
                    <ToggleButton
                        value="justify"
                        onClick={() => editor.chain().focus().setTextAlign('justify').run()}
                        aria-label="align justify"
                    >
                        <FormatAlignJustify fontSize="small" />
                    </ToggleButton>
                </ToggleButtonGroup>

                <Divider flexItem orientation="vertical" sx={{ mx: 0.5, my: 1 }} />

                <ToggleButtonGroup size="small">
                    <ToggleButton
                        value="undo"
                        onClick={() => editor.chain().focus().undo().run()}
                        disabled={!editor.can().chain().focus().undo().run()}
                        aria-label="undo"
                    >
                        <Undo fontSize="small" />
                    </ToggleButton>
                    <ToggleButton
                        value="redo"
                        onClick={() => editor.chain().focus().redo().run()}
                        disabled={!editor.can().chain().focus().redo().run()}
                        aria-label="redo"
                    >
                        <Redo fontSize="small" />
                    </ToggleButton>
                </ToggleButtonGroup>
            </Toolbar>
        </Paper>
    );
};

const normalizeFuzzy = (text) => {
    if (!text) return '';
    return text.toLowerCase().replace(/[^a-z0-9]/g, '');
};

const RichTextEditor = forwardRef(({ content = '', onUpdate, readOnly = false, reviewPoints = [], activePointId, onPointSelect, onSelectionChange, pendingReplacement, onReplacementApplied }, ref) => {
    const [_, forceUpdate] = React.useReducer((x) => x + 1, 0);

    const editor = useEditor({
        extensions: [
            StarterKit,
            Highlight.configure({ multipart: true }),
            TextStyle,
            FontFamily,
            FontSize,
            TextAlign.configure({
                types: ['heading', 'paragraph'],
            }),
            Table.configure({ resizable: false }),
            TableRow,
            TableCell,
            TableHeader,
        ],
        content: content,
        editable: !readOnly,
        onUpdate: ({ editor, transaction }) => {
            const isHighlight = transaction.getMeta('isHighlightUpdate');
            const isSuggestion = transaction.getMeta('isSuggestionApplication');
            if (onUpdate) {
                onUpdate(editor.getHTML(), isHighlight, isSuggestion);
            }
            forceUpdate();
        },
        onTransaction: () => {
            forceUpdate();
        },
        onSelectionUpdate: ({ editor }) => {
            forceUpdate();

            const { from, to, empty } = editor.state.selection;

            // Notify parent about selection for the "Selection Context" badge
            if (onSelectionChange) {
                if (empty) {
                    onSelectionChange(null);
                } else {
                    onSelectionChange(editor.state.doc.textBetween(from, to, ' '));
                }
            }

            // Reverse Linking: Selection -> Review Point
            if (onPointSelect && !empty) {
                const selectedText = editor.state.doc.textBetween(from, to, ' ');

                if (selectedText && selectedText.length > 5 && reviewPoints.length > 0) {
                    const normalizedSelected = normalizeFuzzy(selectedText);
                    const matchedPoint = reviewPoints.find(p => {
                        const normalizedPoint = normalizeFuzzy(p.originalText);
                        return normalizedPoint && (
                            normalizedSelected.includes(normalizedPoint) ||
                            normalizedPoint.includes(normalizedSelected)
                        );
                    });

                    if (matchedPoint) {
                        onPointSelect(matchedPoint.id);
                    }
                }
            }
        },
        editorProps: {
            attributes: {
                class: 'prose prose-sm sm:prose lg:prose-lg xl:prose-2xl focus:outline-none min-h-[300px] px-4 py-2',
            },
        },
    });

    // Expose methods to parent via ref
    const [recentlyUpdated, setRecentlyUpdated] = React.useState(false);

    React.useEffect(() => {
        if (recentlyUpdated) {
            const timer = setTimeout(() => setRecentlyUpdated(false), 2000);
            return () => clearTimeout(timer);
        }
    }, [recentlyUpdated]);

    useImperativeHandle(ref, () => ({
        getSelectionContext: () => {
            if (!editor) return { text: null, html: null };
            const { from, to } = editor.state.selection;
            return {
                text: editor.state.doc.textBetween(from, to),
                html: editor.getHTML() // Simplification: in a real app would extract subset
            };
        },
        applyPartialUpdate: (newFragment) => {
            if (!editor) return false;

            const { from, to, empty } = editor.state.selection;
            console.log(`[RichTextEditor] applyPartialUpdate. Range: ${from}-${to}, Empty: ${empty}, Focused: ${editor.isFocused}`);
            console.log(`[RichTextEditor] Content to insert: ${newFragment.substring(0, 100)}...`);

            const chain = editor.chain().focus().setMeta('isSuggestionApplication', true);

            if (!empty) {
                // Replace selection (AI's target)
                chain.unsetAllMarks().deleteRange({ from, to }).insertContent(newFragment).run();
            } else {
                // FALLBACK: Insert at current cursor position
                // Since AI didn't provide metadata, and user didn't select, 
                // cursor is the only deterministic target.
                chain.unsetAllMarks().insertContent(newFragment).run();
            }

            setRecentlyUpdated(true);
            return true;
        }
    }), [editor]);

    // Handle Text Replacement (Accept Suggestion)
    React.useEffect(() => {
        if (!editor || !pendingReplacement) return;

        const { originalText, newText, pointId } = pendingReplacement;
        console.log(`🔄 Applying replacement: "${originalText}" -> "${newText}"`);

        const range = findTextPosition(editor.state.doc, originalText);

        if (range) {
            editor.chain()
                .focus()
                .setMeta('isSuggestionApplication', true) // Flag this as an automated AI edit
                .insertContentAt(range, newText)
                .scrollIntoView()
                .run();

            showSuccessToast('Suggestion applied to document');

            if (onReplacementApplied) {
                onReplacementApplied(true, pointId);
            }
        } else {
            console.error(`❌ Could not find text for replacement: "${originalText}"`);
            showErrorToast('Could not find the text to replace in the document.');

            if (onReplacementApplied) {
                onReplacementApplied(false, pointId);
            }
        }
    }, [editor, pendingReplacement, onReplacementApplied]);

    // Helper to find text position across multiple nodes with robust fuzzy (alphanumeric, case-insensitive) matching
    const findTextPosition = (doc, searchText) => {
        if (!searchText) return null;

        let rawText = '';
        const rawPosMap = []; // index in rawText -> document position

        // 1. Traverse document to build raw text and position map
        doc.descendants((node, pos) => {
            if (node.isText) {
                for (let i = 0; i < node.text.length; i++) {
                    rawText += node.text[i];
                    rawPosMap.push(pos + i);
                }
            } else if (node.isBlock || node.type.name === 'hardBreak') {
                rawText += ' '; // Convert blocks/breaks to spaces for search
                rawPosMap.push(pos);
            }
        });

        // 2. Build FUZZY text (lowercase alphanumeric only) and map it back to rawText indices
        let fuzzyText = '';
        const fuzzyToRawMap = []; // index in fuzzyText -> index in rawText

        for (let i = 0; i < rawText.length; i++) {
            const char = rawText[i].toLowerCase();
            // Match only alphanumeric characters for the fuzzy search
            if (/[a-z0-9]/.test(char)) {
                fuzzyText += char;
                fuzzyToRawMap.push(i);
            }
        }

        // 3. Normalize search query to fuzzy format
        let fuzzyQuery = '';
        for (let i = 0; i < searchText.length; i++) {
            const char = searchText[i].toLowerCase();
            if (/[a-z0-9]/.test(char)) {
                fuzzyQuery += char;
            }
        }

        if (!fuzzyQuery) return null;

        // 4. Search in fuzzy text
        const matchIndex = fuzzyText.indexOf(fuzzyQuery);
        if (matchIndex === -1) return null;

        // 5. Map match range back to document positions
        // We get the start and end of the match in the FUZZY string
        const startFuzzyIdx = matchIndex;
        const endFuzzyIdx = matchIndex + fuzzyQuery.length - 1;

        // Map these back to RAW indices
        const startRawIndex = fuzzyToRawMap[startFuzzyIdx];
        const endRawIndex = fuzzyToRawMap[endFuzzyIdx];

        // Finally map back to ProseMirror positions
        const from = rawPosMap[startRawIndex];
        const to = rawPosMap[endRawIndex] + 1;

        return { from, to };
    };

    // Handle Active Point: Highlight and Scroll
    React.useEffect(() => {
        if (editor && activePointId && reviewPoints.length > 0) {
            const point = reviewPoints.find(p => p.id === activePointId);
            if (point && point.originalText) {
                // Find text position using robust multi-node search
                const foundPos = findTextPosition(editor.state.doc, point.originalText);

                if (foundPos) {
                    // 1. Clear ALL existing highlights first to ensure only one is active
                    // 2. Set selection and apply new highlight
                    // Use setMeta to flag this as a highlight update
                    editor.chain()
                        .setMeta('isHighlightUpdate', true)
                        .focus()
                        .selectAll()
                        .unsetHighlight()
                        .setTextSelection(foundPos)
                        .setHighlight({ color: '#ffecb3' })
                        .scrollIntoView()
                        .run();
                } else {
                    console.warn(`Could not find text for highlight: "${point.originalText}"`);
                }
            }
        } else if (editor && !activePointId) {
            // Clear highlights if no point is selected
            editor.chain()
                .setMeta('isHighlightUpdate', true)
                .focus()
                .selectAll()
                .unsetHighlight()
                .setTextSelection(0)
                .run();
        }
    }, [activePointId, editor, reviewPoints]);

    // Update editor content when content prop changes
    React.useEffect(() => {
        if (editor && content) {
            if (editor.getHTML() !== content) {
                editor.commands.setContent(content, false, { preserveWhitespace: 'full' });
                // If this prop update came from setExternalHtml in index.jsx, 
                // we want it to trigger the isSuggestion auto-save in DocumentViewer
                editor.view.dispatch(editor.state.tr.setMeta('isSuggestionApplication', true));
            }
        }
    }, [content, editor]);

    return (
        <Box
            className={recentlyUpdated ? 'suggestion-applied' : ''}
            sx={{
                border: 1,
                borderColor: 'divider',
                borderRadius: 1,
                bgcolor: 'background.paper',
                display: 'flex',
                flexDirection: 'column',
                height: '100%',
                transition: 'all 0.3s ease'
            }}
        >
            <MenuBar editor={editor} />
            <Box sx={{ flexGrow: 1, overflowY: 'auto', p: 2 }}>
                <EditorContent editor={editor} style={{ height: '100%' }} />
            </Box>
            <style>{`
                .suggestion-applied {
                    animation: pulse-border 1s ease-in-out;
                }
                @keyframes pulse-border {
                    0% { border-color: #dee2e6; box-shadow: 0 0 0 rgba(0, 123, 255, 0); }
                    50% { border-color: #0d6efd; box-shadow: 0 0 10px rgba(13, 110, 253, 0.5); }
                    100% { border-color: #dee2e6; box-shadow: 0 0 0 rgba(0, 123, 255, 0); }
                }
                .ProseMirror {
                    height: 100%;
                    outline: none;
                    font-family: 'Inter', sans-serif;
                    text-align: left;
                    width: 100%;
                }
                .ProseMirror p.is-editor-empty:first-child::before {
                    color: #adb5bd;
                    content: attr(data-placeholder);
                    float: left;
                    height: 0;
                    pointer-events: none;
                }
                /* Custom highlight style */
                mark {
                    background-color: #ffecb3;
                    border-radius: 2px;
                    padding: 0 2px;
                }
                /* Table styles from backend HTML */
                .ProseMirror table {
                    border-collapse: collapse;
                    margin: 1em 0;
                    width: 100%;
                    table-layout: auto;
                }
                .ProseMirror th,
                .ProseMirror td {
                    border: 1px solid #000;
                    padding: 6px 8px;
                    vertical-align: top;
                    text-align: left;
                    min-width: 60px;
                }
                .ProseMirror th {
                    background: #f5f5f5;
                    font-weight: bold;
                }
                /* Preserve &nbsp; spacing from backend HTML */
                .ProseMirror p {
                    white-space: pre-wrap;
                }
            `}</style>
        </Box>
    );
});

export default RichTextEditor;
