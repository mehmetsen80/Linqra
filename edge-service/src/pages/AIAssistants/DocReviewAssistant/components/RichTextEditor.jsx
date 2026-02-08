import React from 'react';
import { useEditor, EditorContent } from '@tiptap/react';
import { Extension } from '@tiptap/core';
import StarterKit from '@tiptap/starter-kit';
import Highlight from '@tiptap/extension-highlight';
import Underline from '@tiptap/extension-underline';
import { TextStyle } from '@tiptap/extension-text-style';
import { FontFamily } from '@tiptap/extension-font-family';
import TextAlign from '@tiptap/extension-text-align';
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

const RichTextEditor = ({ content = '', onUpdate, readOnly = false, reviewPoints = [], activePointId, onPointSelect }) => {
    // Force re-render when editor state changes (selection, content, etc.)
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
        ],
        content: content,
        editable: !readOnly,
        onUpdate: ({ editor }) => {
            if (onUpdate) {
                onUpdate(editor.getHTML());
            }
            forceUpdate();
        },
        onSelectionUpdate: ({ editor }) => {
            forceUpdate();

            // Reverse Linking: Selection -> Review Point
            if (onPointSelect && !editor.state.selection.empty) {
                const selectedText = editor.state.doc.textBetween(
                    editor.state.selection.from,
                    editor.state.selection.to,
                    ' '
                );

                if (selectedText && selectedText.length > 5 && reviewPoints.length > 0) {
                    // Find a point that matches this text
                    // We use partial matching or exact matching? Exact is safer for now.
                    // Or check if the selected text contains the point's text (looser).
                    const matchedPoint = reviewPoints.find(p =>
                        p.originalText && (
                            selectedText.includes(p.originalText) ||
                            p.originalText.includes(selectedText)
                        )
                    );

                    if (matchedPoint) {
                        onPointSelect(matchedPoint.id);
                    }
                }
            }
        },
        onTransaction: () => {
            forceUpdate();
        },
        editorProps: {
            attributes: {
                class: 'prose prose-sm sm:prose lg:prose-lg xl:prose-2xl focus:outline-none min-h-[300px] px-4 py-2',
            },
        },
    });

    // Handle Active Point: Highlight and Scroll
    React.useEffect(() => {
        if (editor && activePointId && reviewPoints.length > 0) {
            const point = reviewPoints.find(p => p.id === activePointId);
            if (point && point.originalText) {
                // Find text position
                let foundPos = null;

                // Simple search in text nodes
                // TODO: Handle text spanning multiple nodes if needed
                editor.state.doc.descendants((node, pos) => {
                    if (foundPos) return false;
                    if (node.isText) {
                        const idx = node.text.indexOf(point.originalText);
                        if (idx !== -1) {
                            foundPos = { from: pos + idx, to: pos + idx + point.originalText.length };
                        }
                    }
                });

                if (foundPos) {
                    // Clear previous highlights first? Maybe not needed if we just select.
                    // We simply set selection to the found text.
                    // Tiptap's highlight extension requires us to apply mark.

                    editor.chain()
                        .focus()
                        .setTextSelection(foundPos)
                        .unsetHighlight() // Clear old highlight on this selection if any
                        .setHighlight({ color: '#ffecb3' }) // Apply new highlight
                        .scrollIntoView()
                        .run();
                }
            }
        }
    }, [activePointId, editor, reviewPoints]);

    // Update editor content when content prop changes
    React.useEffect(() => {
        if (editor && content) {
            if (editor.getHTML() !== content) {
                editor.commands.setContent(content);
            }
        }
    }, [content, editor]);

    return (
        <Box sx={{
            border: 1,
            borderColor: 'divider',
            borderRadius: 1,
            bgcolor: 'background.paper',
            display: 'flex',
            flexDirection: 'column',
            height: '100%'
        }}>
            <MenuBar editor={editor} />
            <Box sx={{ flexGrow: 1, overflowY: 'auto', p: 2 }}>
                <EditorContent editor={editor} style={{ height: '100%' }} />
            </Box>
            <style>{`
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
            `}</style>
        </Box>
    );
};

export default RichTextEditor;
